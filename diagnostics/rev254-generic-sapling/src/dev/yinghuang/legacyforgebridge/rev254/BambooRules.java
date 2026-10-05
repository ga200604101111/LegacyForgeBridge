package dev.yinghuang.legacyforgebridge.rev254;
/** Source-binary-bound rules, NOT a claim of generic Entity->Particle or arbitrary plant conversion. */
public final class BambooRules {
 public static final String SOURCE_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
 public static final String LEAF_SOURCE_CLASS="ruby/bamboo/block/BlockSakuraLeaves";
 public static final String SAPLING_SOURCE_CLASS="ruby/bamboo/block/BlockSakura";
 public static final String SOURCE_CLASS=LEAF_SOURCE_CLASS;
 private static final int[] COLORS={6052956,13107216,4169301,9991243,6261212,11569100,8903670,13882323,9276813,16762316,12383346,16115200,12120063,16746490,16762368,16777215};
 private static final int[] VARIANTS={1,2,0,0,1,1,1,2,1,1,0,3,1,1,3,1};
 public static boolean hash(String hash){return SOURCE_SHA256.equalsIgnoreCase(hash);}
 public static boolean admitLeaf(String hash,String source){return hash(hash) && source!=null && LEAF_SOURCE_CLASS.equals(source.replace('.','/'));}
 public static boolean admit(String hash,String source){return admitLeaf(hash,source);}
 public static boolean admitSapling(String hash,String source){return hash(hash) && source!=null && SAPLING_SOURCE_CLASS.equals(source.replace('.','/'));}
 public static int color(int meta){return COLORS[index(meta)];}
 public static int variant(int meta){return VARIANTS[index(meta)];}
 private static int index(int meta){if(meta<0||meta>=16)throw new IllegalArgumentException("Legacy metadata must be 0..15");return meta;}
 public static double fallingVelocity(double vy){return vy-.004D;}
 public static double damping(double velocity){return velocity*.95D;}
 private BambooRules(){}
}