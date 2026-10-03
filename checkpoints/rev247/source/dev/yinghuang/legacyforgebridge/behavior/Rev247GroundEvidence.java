package dev.yinghuang.legacyforgebridge.behavior;

/** Supplements rev245 labels only. No game or packet references, no movement writes. */
public final class Rev247GroundEvidence {
    private static long jump,positiveCount,lastPositiveNs=-1,groundNs=-1,groundJump;
    private static String groundY="unknown";
    private Rev247GroundEvidence() {}
    public static synchronized String decorate(String stage,String detail,long ns,long localJump) {
        String original=Rev245VelocityCorrelation.decorate(stage,detail,ns,localJump);
        if(detail==null)detail="";
        if("CAPTURE_START".equals(stage)) {
            jump=positiveCount=groundJump=0;lastPositiveNs=groundNs=-1;groundY="unknown";
            return original+" groundEvidenceSchema=rev247.1";
        }
        if("GROUND_CONTACT".equals(stage)) {
            groundNs=ns;groundJump=localJump;groundY=vectorAxis(detail,"pos=",1);
            return original+" observedLandingY="+groundY+" retainedPositiveHistory=true";
        }
        if(!"CLIENT_APPLY_HEAD".equals(stage))return original;
        if(jump!=localJump){jump=localJump;positiveCount=0;lastPositiveNs=-1;}
        double vy;
        try {vy=Double.parseDouble(vectorAxis(detail,"incoming=",1));}catch(NumberFormatException ex){return original;}
        if(!Double.isFinite(vy)||vy<=1e-9)return original;
        positiveCount++;
        long since=groundNs<0?-1:ns-groundNs;
        boolean recent=since>=0&&since<=2_000_000_000L;
        String ground=token(detail,"ground=");
        String out=" sourceJumpPositiveOrdinal="+positiveCount+" sourceJumpLabel="+localJump;
        if(recent)out+=" sinceObservedGroundUs="+(since/1000)+" lastObservedLandingY="+groundY;
        boolean repeat=positiveCount>1&&lastPositiveNs>=0&&ns>=lastPositiveNs&&ns-lastPositiveNs<=2_000_000_000L;
        if(repeat)out+=" repeatedPositiveForLocalJump=true diagnosticCausalProof=false";
        if(repeat&&recent&&groundJump==localJump)
            out+=" postGroundPositiveCandidate=true groundAtApply="+(ground==null?"unknown":ground)
                +" candidateMeaning=same_local_jump_positive_after_observed_ground_not_server_proof";
        lastPositiveNs=ns;
        return original+out;
    }
    private static String token(String text,String key){
        int i=text.indexOf(key);while(i>0&&!Character.isWhitespace(text.charAt(i-1)))i=text.indexOf(key,i+1);
        if(i<0)return null;int b=i+key.length(),e=b;while(e<text.length()&&!Character.isWhitespace(text.charAt(e)))e++;
        return text.substring(b,e);
    }
    private static String vectorAxis(String text,String key,int axis){
        String v=token(text,key);if(v==null||!v.startsWith("[")||!v.endsWith("]"))return "unknown";
        String[] parts=v.substring(1,v.length()-1).split(",",-1);return parts.length==3?parts[axis]:"unknown";
    }
}
