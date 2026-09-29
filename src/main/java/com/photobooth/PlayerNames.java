package com.photobooth;

import java.util.regex.Pattern;

final class PlayerNames
{
    //display names: 1-12 chars, letters/digits/space/underscore/hyphen
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9 _-]{1,12}$");

    private PlayerNames()
    {
    }

    static String normalize(String name)
    {
        return name.replace('\u00A0', ' ').trim();
    }

    static boolean isValid(String name)
    {
        return VALID_NAME.matcher(name).matches();
    }
}