package com.darkgolly.util;

public class Spliter {
    public static String split(String str) {
        str = str.split("&list")[0];
        return str;
    }
}
