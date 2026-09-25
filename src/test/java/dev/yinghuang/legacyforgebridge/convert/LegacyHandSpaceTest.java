package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Compare complete affine chains at the hand, not just a list of preferred constants. */
class LegacyHandSpaceTest {
    @Test void modernAttachmentTimesBridgeEqualsLegacyNeutralAttachmentForEveryItemBasis() {
        for(boolean full:List.of(false,true)) for(boolean rotates:List.of(false,true)) {
            Matrix modern=new Matrix().r(-90,1,0,0).r(180,0,1,0).t(1D/16,.125,-.625);
            Matrix bridged=modern.mul(operations(LegacyHandSpace.thirdPerson(full,rotates,false)));
            Matrix old=new Matrix().t(-1D/16,7D/16,1D/16);
            if(full){
                if(rotates)old.r(180,0,0,1).t(0,-.125,0);
                old.t(0,3D/16,0).s(.625,-.625,.625).r(-100,1,0,0).r(45,0,1,0);
            }else old.t(.25,3D/16,-3D/16).s(.375,.375,.375).r(60,0,0,1).r(-90,1,0,0).r(20,0,1,0);
            old.t(0,-.3,0).s(1.5,1.5,1.5).r(50,0,1,0).r(335,0,0,1).t(-15D/16,-1D/16,0);
            assertArrayEquals(old.a,bridged.a,1E-6,"full="+full+", rotate="+rotates);
            // Blocking belongs to Via/vanilla. LFB only converts the neutral source hand basis.
            assertArrayEquals(old.point(.17,-.4,1.2),bridged.point(.17,-.4,1.2),1E-6);
        }
    }
    @Test void leftHandIsReflectionConjugateAndSourceOperationOrderIsRetained() {
        Matrix reflect=new Matrix().s(-1,1,1);
        var right=LegacyHandSpace.thirdPerson(true,false,false);
        assertArrayEquals(new Matrix().mul(reflect).mul(operations(right)).mul(reflect).a,
                operations(LegacyHandSpace.thirdPerson(true,false,true)).a,1E-6);
        var authored=List.of(LegacyItemRenderAnalyzer.Operation.of("translate",2,3,4),
                LegacyItemRenderAnalyzer.Operation.of("rotate",37,0,1,0),LegacyItemRenderAnalyzer.Operation.of("scale",2,1,3));
        assertArrayEquals(new Matrix().mul(reflect).mul(operations(authored)).mul(reflect).a,
                operations(LegacyHandSpace.mirror(authored)).a,1E-6);
    }
    private static Matrix operations(List<LegacyItemRenderAnalyzer.Operation> ops){
        Matrix m=new Matrix();for(var op:ops){var v=op.values();switch(op.op()){
            case "translate"->m.t(v.get(0),v.get(1),v.get(2));case "scale"->m.s(v.get(0),v.get(1),v.get(2));
            case "rotate"->m.r(v.get(0),v.get(1),v.get(2),v.get(3));default->throw new AssertionError(op.op());
        }}return m;
    }
    private static final class Matrix {
        double[] a={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
        Matrix mul(Matrix b){double[] n=new double[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)for(int k=0;k<4;k++)n[4*r+c]+=a[4*r+k]*b.a[4*k+c];a=n;return this;}
        Matrix t(double x,double y,double z){Matrix b=new Matrix();b.a[3]=x;b.a[7]=y;b.a[11]=z;return mul(b);}
        Matrix s(double x,double y,double z){Matrix b=new Matrix();b.a[0]=x;b.a[5]=y;b.a[10]=z;return mul(b);}
        Matrix r(double degrees,double x,double y,double z){double n=Math.sqrt(x*x+y*y+z*z);x/=n;y/=n;z/=n;double c=Math.cos(Math.toRadians(degrees)),s=Math.sin(Math.toRadians(degrees)),v=1-c;Matrix b=new Matrix();b.a=new double[]{x*x*v+c,x*y*v-z*s,x*z*v+y*s,0,y*x*v+z*s,y*y*v+c,y*z*v-x*s,0,z*x*v-y*s,z*y*v+x*s,z*z*v+c,0,0,0,0,1};return mul(b);}
        double[] point(double x,double y,double z){return new double[]{a[0]*x+a[1]*y+a[2]*z+a[3],a[4]*x+a[5]*y+a[6]*z+a[7],a[8]*x+a[9]*y+a[10]*z+a[11]};}
    }
}
