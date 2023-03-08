package io.github.coolcrabs.testmod.template;

public class Template {
	public static final boolean BOOLEAN = Boolean.parseBoolean("${boolean}");
	public static final byte    BYTE    = Byte   .parseByte   ("${byte}");
	public static final short   SHORT   = Short  .parseShort  ("${short}");
	public static final int     INT     = Integer.parseInt    ("${int}");
	public static final long    LONG    = Long   .parseLong   ("${long}");
	public static final String  STRING  = "${string}";
}