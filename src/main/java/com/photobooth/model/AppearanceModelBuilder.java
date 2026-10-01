package com.photobooth.model;

import com.photobooth.contract.Appearance;
import com.photobooth.contract.EquipmentSlot;
import com.photobooth.contract.SlotEntry;
import com.photobooth.contract.Usernames;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.kit.KitType;

/**
 * Turns an {@link Appearance} into a {@link MeshSnapshot} using the game's own model builder.
 * <p>
 * RuneLite exposes no API to build a player model from equipment IDs directly (ItemComposition
 * has no wear-model IDs, and there is no way to create a PlayerComposition). So we borrow a
 * loaded player's composition ("donor"), temporarily write the appearance into it, ask the
 * client for the model, copy the geometry, and restore the donor, all inside a single
 * client-thread task, so no frame is ever drawn with the swapped appearance.
 * <p>
 * The client applies all item rules itself (helmets hiding hair, platebodies hiding arms,
 * etc.), which is why this beats assembling models by hand.
 * <p>
 * Limitation: gender cannot be changed through the API, so the donor must have the same
 * gender as the appearance. The donor's animation state is also swapped to a still idle pose
 * (the target's own weapon stance when known) and restored afterwards.
 * <p>
 * CLIENT THREAD ONLY.
 */
@Slf4j
@Singleton
public class AppearanceModelBuilder
{
	private final Client client;

	@Inject
	AppearanceModelBuilder(Client client)
	{
		this.client = client;
	}

	public ModelBuildResult build(Appearance appearance)
	{
		assert client.isClientThread() : "AppearanceModelBuilder must run on the client thread";

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return ModelBuildResult.failure("Log in to render characters.");
		}

		Player donor = pickDonor(appearance);
		if (donor != null)
		{
			log.debug("Photobooth donor {} (idle pose {}), rendering {} with idle pose {}",
				donor.getName(), donor.getIdlePoseAnimation(),
				appearance.getDisplayName(), idlePoseFor(appearance));
		}
		if (donor == null)
		{
			String gender = appearance.getGender() == Appearance.GENDER_FEMALE ? "female" : "male";
			return ModelBuildResult.failure("Rendering this character needs a " + gender
				+ " character loaded near you (a game limitation).");
		}

		PlayerComposition comp = donor.getPlayerComposition();
		int[] equipment = comp.getEquipmentIds();
		int[] colors = comp.getColors();
		int[] savedEquipment = equipment.clone();
		int[] savedColors = colors.clone();
		int savedNpc = comp.getTransformedNpcId();

		// Animation state, so the donor's walking/running/attacking never leaks into the pose.
		int savedAnimation = donor.getAnimation();
		int savedAnimationFrame = donor.getAnimationFrame();
		int savedPose = donor.getPoseAnimation();
		int savedPoseFrame = donor.getPoseAnimationFrame();

		try
		{
			writeAppearance(appearance, equipment, colors);
			comp.setTransformedNpcId(appearance.getNpcTransformId() == null ? -1 : appearance.getNpcTransformId());
			comp.setHash();

			// Force a clean idle stance: no action animation, idle pose on its first frame.
			donor.setAnimation(-1);
			donor.setAnimationFrame(0);
			donor.setPoseAnimation(idlePoseFor(appearance));
			donor.setPoseAnimationFrame(0);

			Model model = donor.getModel();
			if (model == null)
			{
				return ModelBuildResult.failure("The game didn't return a model. Try again in a moment.");
			}
			return ModelBuildResult.success(snapshot(model));
		}
		catch (RuntimeException e)
		{
			log.warn("Photobooth model build failed", e);
			return ModelBuildResult.failure("Couldn't build this character's model.");
		}
		finally
		{
			System.arraycopy(savedEquipment, 0, equipment, 0, savedEquipment.length);
			System.arraycopy(savedColors, 0, colors, 0, savedColors.length);
			comp.setTransformedNpcId(savedNpc);
			comp.setHash();
			donor.setAnimation(savedAnimation);
			donor.setAnimationFrame(savedAnimationFrame);
			donor.setPoseAnimation(savedPose);
			donor.setPoseAnimationFrame(savedPoseFrame);
		}
	}

	/**
	 * The target's own weapon stance if we captured it; otherwise the standard unarmed stand.
	 * 808 is the well-known player stand animation (fairly sure; RuneLite's AnimationID has no
	 * constant for it). Verify: the DEBUG log below prints your own idle pose when unarmed.
	 */
	static final int DEFAULT_IDLE_POSE = 808;

	private static int idlePoseFor(Appearance appearance)
	{
		Integer idle = appearance.getIdlePoseAnimation();
		return idle != null ? idle : DEFAULT_IDLE_POSE;
	}

	/**
	 * Prefer the actual player (exact result, and a no-op swap if their gear is unchanged),
	 * then ourselves, then anyone else of the right gender.
	 */
	private Player pickDonor(Appearance appearance)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		String target = appearance.getNormalizedName();
		Player local = client.getLocalPlayer();
		Player fallback = null;

		for (Player p : worldView.players())
		{
			if (!isCompatible(p, appearance))
			{
				continue;
			}
			if (p.getName() != null && Usernames.normalize(p.getName()).equals(target))
			{
				return p;
			}
			if (fallback == null && p != local)
			{
				fallback = p;
			}
		}
		return isCompatible(local, appearance) ? local : fallback;
	}

	private static boolean isCompatible(Player p, Appearance appearance)
	{
		if (p == null)
		{
			return false;
		}
		PlayerComposition comp = p.getPlayerComposition();
		return comp != null
			&& comp.getGender() == appearance.getGender()
			&& comp.getEquipmentIds() != null
			&& comp.getColors() != null;
	}

	/** Inverse of PlayerAppearanceCapture.decode: back to the client's offset encoding. */
	private static void writeAppearance(Appearance appearance, int[] equipment, int[] colors)
	{
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			int index = KitType.valueOf(slot.name()).getIndex();
			if (index >= equipment.length)
			{
				continue;
			}
			SlotEntry entry = appearance.getEquipment().get(slot);
			if (entry == null)
			{
				equipment[index] = 0;
			}
			else if (entry.getType() == SlotEntry.Type.ITEM)
			{
				equipment[index] = entry.getId() + PlayerComposition.ITEM_OFFSET;
			}
			else
			{
				equipment[index] = entry.getId() + PlayerComposition.KIT_OFFSET;
			}
		}

		int[] wanted = appearance.getBodyColors();
		System.arraycopy(wanted, 0, colors, 0, Math.min(wanted.length, colors.length));
	}

	/** Copy immediately: the client reuses/mutates this Model after we return. */
	private static MeshSnapshot snapshot(Model m)
	{
		return new MeshSnapshot(
			m.getVerticesCount(), m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(),
			m.getFaceCount(), m.getFaceIndices1(), m.getFaceIndices2(), m.getFaceIndices3(),
			m.getFaceColors1(), m.getFaceColors2(), m.getFaceColors3(),
			m.getFaceTransparencies(), m.getFaceRenderPriorities());
	}
}
