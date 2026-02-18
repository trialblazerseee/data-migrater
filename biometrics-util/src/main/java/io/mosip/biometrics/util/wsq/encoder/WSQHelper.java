package io.mosip.biometrics.util.wsq.encoder;

import java.util.ArrayList;
import java.util.List;

public class WSQHelper {
    static int[] BITMASK = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255};
    static final int MAX_DHT_TABLES = 8;
    static final int MAX_HUFFBITS = 16;
    static final int MAX_HUFFCOUNTS_WSQ = 256;
    static final int MAX_HUFFCOEFF = 74;
    static final int MAX_HUFFZRUN = 100;
    static final int MAX_HIFILT = 7;
    static final int MAX_LOFILT = 9;
    static final int W_TREELEN = 20;
    static final int Q_TREELEN = 64;
    static final int SOI_WSQ = 65440;
    static final int EOI_WSQ = 65441;
    static final int SOF_WSQ = 65442;
    static final int SOB_WSQ = 65443;
    static final int DTT_WSQ = 65444;
    static final int DQT_WSQ = 65445;
    static final int DHT_WSQ = 65446;
    static final int DRT_WSQ = 65447;
    static final int COM_WSQ = 65448;
    static final int STRT_SUBBAND_2 = 19;
    static final int STRT_SUBBAND_3 = 52;
    static final int MAX_SUBBANDS = 64;
    static final int NUM_SUBBANDS = 60;
    static final int STRT_SUBBAND_DEL = 60;
    static final int STRT_SIZE_REGION_2 = 4;
    static final int STRT_SIZE_REGION_3 = 51;
    static final int COEFF_CODE = 0;
    static final int RUN_CODE = 1;
    static final float VARIANCE_THRESH = 1.01F;
    static final int ANY_WSQ = 65535;
    static final int TBLS_N_SOF = 2;
    static final int TBLS_N_SOB = 4;

    static void buildWSQTrees(Token token, int width, int height) {
        buildWTree(token, 20, width, height);
        buildQTree(token, 64);
    }

    static void buildWTree(Token token, int wtreelen, int width, int height) {
        token.wtree = new WavletTree[wtreelen];

        for(int i = 0; i < wtreelen; ++i) {
            token.wtree[i] = new WavletTree();
            token.wtree[i].invrw = 0;
            token.wtree[i].invcl = 0;
        }

        token.wtree[2].invrw = 1;
        token.wtree[4].invrw = 1;
        token.wtree[7].invrw = 1;
        token.wtree[9].invrw = 1;
        token.wtree[11].invrw = 1;
        token.wtree[13].invrw = 1;
        token.wtree[16].invrw = 1;
        token.wtree[18].invrw = 1;
        token.wtree[3].invcl = 1;
        token.wtree[5].invcl = 1;
        token.wtree[8].invcl = 1;
        token.wtree[9].invcl = 1;
        token.wtree[12].invcl = 1;
        token.wtree[13].invcl = 1;
        token.wtree[17].invcl = 1;
        token.wtree[18].invcl = 1;
        wtree4(token, 0, 1, width, height, 0, 0, 1);
        int lenx;
        int lenx2;
        if (token.wtree[1].lenx % 2 == 0) {
            lenx = token.wtree[1].lenx / 2;
            lenx2 = lenx;
        } else {
            lenx = (token.wtree[1].lenx + 1) / 2;
            lenx2 = lenx - 1;
        }

        int leny;
        int leny2;
        if (token.wtree[1].leny % 2 == 0) {
            leny = token.wtree[1].leny / 2;
            leny2 = leny;
        } else {
            leny = (token.wtree[1].leny + 1) / 2;
            leny2 = leny - 1;
        }

        wtree4(token, 4, 6, lenx2, leny, lenx, 0, 0);
        wtree4(token, 5, 10, lenx, leny2, 0, leny, 0);
        wtree4(token, 14, 15, lenx, leny, 0, 0, 0);
        token.wtree[19].x = 0;
        token.wtree[19].y = 0;
        if (token.wtree[15].lenx % 2 == 0) {
            token.wtree[19].lenx = token.wtree[15].lenx / 2;
        } else {
            token.wtree[19].lenx = (token.wtree[15].lenx + 1) / 2;
        }

        if (token.wtree[15].leny % 2 == 0) {
            token.wtree[19].leny = token.wtree[15].leny / 2;
        } else {
            token.wtree[19].leny = (token.wtree[15].leny + 1) / 2;
        }

    }

    static void wtree4(Token token, int start1, int start2, int lenx, int leny, int x, int y, int stop1) {
        int evenx = lenx % 2;
        int eveny = leny % 2;
        token.wtree[start1].x = x;
        token.wtree[start1].y = y;
        token.wtree[start1].lenx = lenx;
        token.wtree[start1].leny = leny;
        token.wtree[start2].x = x;
        token.wtree[start2 + 2].x = x;
        token.wtree[start2].y = y;
        token.wtree[start2 + 1].y = y;
        if (evenx == 0) {
            token.wtree[start2].lenx = lenx / 2;
            token.wtree[start2 + 1].lenx = token.wtree[start2].lenx;
        } else if (start1 == 4) {
            token.wtree[start2].lenx = (lenx - 1) / 2;
            token.wtree[start2 + 1].lenx = token.wtree[start2].lenx + 1;
        } else {
            token.wtree[start2].lenx = (lenx + 1) / 2;
            token.wtree[start2 + 1].lenx = token.wtree[start2].lenx - 1;
        }

        token.wtree[start2 + 1].x = token.wtree[start2].lenx + x;
        if (stop1 == 0) {
            token.wtree[start2 + 3].lenx = token.wtree[start2 + 1].lenx;
            token.wtree[start2 + 3].x = token.wtree[start2 + 1].x;
        }

        token.wtree[start2 + 2].lenx = token.wtree[start2].lenx;
        if (eveny == 0) {
            token.wtree[start2].leny = leny / 2;
            token.wtree[start2 + 2].leny = token.wtree[start2].leny;
        } else if (start1 == 5) {
            token.wtree[start2].leny = (leny - 1) / 2;
            token.wtree[start2 + 2].leny = token.wtree[start2].leny + 1;
        } else {
            token.wtree[start2].leny = (leny + 1) / 2;
            token.wtree[start2 + 2].leny = token.wtree[start2].leny - 1;
        }

        token.wtree[start2 + 2].y = token.wtree[start2].leny + y;
        if (stop1 == 0) {
            token.wtree[start2 + 3].leny = token.wtree[start2 + 2].leny;
            token.wtree[start2 + 3].y = token.wtree[start2 + 2].y;
        }

        token.wtree[start2 + 1].leny = token.wtree[start2].leny;
    }

    static void buildQTree(Token token, int qtreelen) {
        token.qtree = new QuantTree[qtreelen];

        for(int i = 0; i < token.qtree.length; ++i) {
            token.qtree[i] = new QuantTree();
        }

        qtree16(token, 3, token.wtree[14].lenx, token.wtree[14].leny, token.wtree[14].x, token.wtree[14].y, 0, 0);
        qtree16(token, 19, token.wtree[4].lenx, token.wtree[4].leny, token.wtree[4].x, token.wtree[4].y, 0, 1);
        qtree16(token, 48, token.wtree[0].lenx, token.wtree[0].leny, token.wtree[0].x, token.wtree[0].y, 0, 0);
        qtree16(token, 35, token.wtree[5].lenx, token.wtree[5].leny, token.wtree[5].x, token.wtree[5].y, 1, 0);
        qtree4(token, 0, token.wtree[19].lenx, token.wtree[19].leny, token.wtree[19].x, token.wtree[19].y);
    }

    static void qtree16(Token token, int start, int lenx, int leny, int x, int y, int rw, int cl) {
        int evenx = lenx % 2;
        int eveny = leny % 2;
        int tempx;
        int temp2x;
        if (evenx == 0) {
            tempx = lenx / 2;
            temp2x = tempx;
        } else if (cl != 0) {
            temp2x = (lenx + 1) / 2;
            tempx = temp2x - 1;
        } else {
            tempx = (lenx + 1) / 2;
            temp2x = tempx - 1;
        }

        int tempy;
        int temp2y;
        if (eveny == 0) {
            tempy = leny / 2;
            temp2y = tempy;
        } else if (rw != 0) {
            temp2y = (leny + 1) / 2;
            tempy = temp2y - 1;
        } else {
            tempy = (leny + 1) / 2;
            temp2y = tempy - 1;
        }

        evenx = tempx % 2;
        eveny = tempy % 2;
        token.qtree[start].x = x;
        token.qtree[start + 2].x = x;
        token.qtree[start].y = y;
        token.qtree[start + 1].y = y;
        if (evenx == 0) {
            token.qtree[start].lenx = tempx / 2;
            token.qtree[start + 1].lenx = token.qtree[start].lenx;
            token.qtree[start + 2].lenx = token.qtree[start].lenx;
            token.qtree[start + 3].lenx = token.qtree[start].lenx;
        } else {
            token.qtree[start].lenx = (tempx + 1) / 2;
            token.qtree[start + 1].lenx = token.qtree[start].lenx - 1;
            token.qtree[start + 2].lenx = token.qtree[start].lenx;
            token.qtree[start + 3].lenx = token.qtree[start + 1].lenx;
        }

        token.qtree[start + 1].x = x + token.qtree[start].lenx;
        token.qtree[start + 3].x = token.qtree[start + 1].x;
        if (eveny == 0) {
            token.qtree[start].leny = tempy / 2;
            token.qtree[start + 1].leny = token.qtree[start].leny;
            token.qtree[start + 2].leny = token.qtree[start].leny;
            token.qtree[start + 3].leny = token.qtree[start].leny;
        } else {
            token.qtree[start].leny = (tempy + 1) / 2;
            token.qtree[start + 1].leny = token.qtree[start].leny;
            token.qtree[start + 2].leny = token.qtree[start].leny - 1;
            token.qtree[start + 3].leny = token.qtree[start + 2].leny;
        }

        token.qtree[start + 2].y = y + token.qtree[start].leny;
        token.qtree[start + 3].y = token.qtree[start + 2].y;
        evenx = temp2x % 2;
        token.qtree[start + 4].x = x + tempx;
        token.qtree[start + 6].x = token.qtree[start + 4].x;
        token.qtree[start + 4].y = y;
        token.qtree[start + 5].y = y;
        token.qtree[start + 6].y = token.qtree[start + 2].y;
        token.qtree[start + 7].y = token.qtree[start + 2].y;
        token.qtree[start + 4].leny = token.qtree[start].leny;
        token.qtree[start + 5].leny = token.qtree[start].leny;
        token.qtree[start + 6].leny = token.qtree[start + 2].leny;
        token.qtree[start + 7].leny = token.qtree[start + 2].leny;
        if (evenx == 0) {
            token.qtree[start + 4].lenx = temp2x / 2;
            token.qtree[start + 5].lenx = token.qtree[start + 4].lenx;
            token.qtree[start + 6].lenx = token.qtree[start + 4].lenx;
            token.qtree[start + 7].lenx = token.qtree[start + 4].lenx;
        } else {
            token.qtree[start + 5].lenx = (temp2x + 1) / 2;
            token.qtree[start + 4].lenx = token.qtree[start + 5].lenx - 1;
            token.qtree[start + 6].lenx = token.qtree[start + 4].lenx;
            token.qtree[start + 7].lenx = token.qtree[start + 5].lenx;
        }

        token.qtree[start + 5].x = token.qtree[start + 4].x + token.qtree[start + 4].lenx;
        token.qtree[start + 7].x = token.qtree[start + 5].x;
        eveny = temp2y % 2;
        token.qtree[start + 8].x = x;
        token.qtree[start + 9].x = token.qtree[start + 1].x;
        token.qtree[start + 10].x = x;
        token.qtree[start + 11].x = token.qtree[start + 1].x;
        token.qtree[start + 8].y = y + tempy;
        token.qtree[start + 9].y = token.qtree[start + 8].y;
        token.qtree[start + 8].lenx = token.qtree[start].lenx;
        token.qtree[start + 9].lenx = token.qtree[start + 1].lenx;
        token.qtree[start + 10].lenx = token.qtree[start].lenx;
        token.qtree[start + 11].lenx = token.qtree[start + 1].lenx;
        if (eveny == 0) {
            token.qtree[start + 8].leny = temp2y / 2;
            token.qtree[start + 9].leny = token.qtree[start + 8].leny;
            token.qtree[start + 10].leny = token.qtree[start + 8].leny;
            token.qtree[start + 11].leny = token.qtree[start + 8].leny;
        } else {
            token.qtree[start + 10].leny = (temp2y + 1) / 2;
            token.qtree[start + 11].leny = token.qtree[start + 10].leny;
            token.qtree[start + 8].leny = token.qtree[start + 10].leny - 1;
            token.qtree[start + 9].leny = token.qtree[start + 8].leny;
        }

        token.qtree[start + 10].y = token.qtree[start + 8].y + token.qtree[start + 8].leny;
        token.qtree[start + 11].y = token.qtree[start + 10].y;
        token.qtree[start + 12].x = token.qtree[start + 4].x;
        token.qtree[start + 13].x = token.qtree[start + 5].x;
        token.qtree[start + 14].x = token.qtree[start + 4].x;
        token.qtree[start + 15].x = token.qtree[start + 5].x;
        token.qtree[start + 12].y = token.qtree[start + 8].y;
        token.qtree[start + 13].y = token.qtree[start + 8].y;
        token.qtree[start + 14].y = token.qtree[start + 10].y;
        token.qtree[start + 15].y = token.qtree[start + 10].y;
        token.qtree[start + 12].lenx = token.qtree[start + 4].lenx;
        token.qtree[start + 13].lenx = token.qtree[start + 5].lenx;
        token.qtree[start + 14].lenx = token.qtree[start + 4].lenx;
        token.qtree[start + 15].lenx = token.qtree[start + 5].lenx;
        token.qtree[start + 12].leny = token.qtree[start + 8].leny;
        token.qtree[start + 13].leny = token.qtree[start + 8].leny;
        token.qtree[start + 14].leny = token.qtree[start + 10].leny;
        token.qtree[start + 15].leny = token.qtree[start + 10].leny;
    }

    static void qtree4(Token token, int start, int lenx, int leny, int x, int y) {
        int evenx = lenx % 2;
        int eveny = leny % 2;
        token.qtree[start].x = x;
        token.qtree[start + 2].x = x;
        token.qtree[start].y = y;
        token.qtree[start + 1].y = y;
        if (evenx == 0) {
            token.qtree[start].lenx = lenx / 2;
            token.qtree[start + 1].lenx = token.qtree[start].lenx;
            token.qtree[start + 2].lenx = token.qtree[start].lenx;
            token.qtree[start + 3].lenx = token.qtree[start].lenx;
        } else {
            token.qtree[start].lenx = (lenx + 1) / 2;
            token.qtree[start + 1].lenx = token.qtree[start].lenx - 1;
            token.qtree[start + 2].lenx = token.qtree[start].lenx;
            token.qtree[start + 3].lenx = token.qtree[start + 1].lenx;
        }

        token.qtree[start + 1].x = x + token.qtree[start].lenx;
        token.qtree[start + 3].x = token.qtree[start + 1].x;
        if (eveny == 0) {
            token.qtree[start].leny = leny / 2;
            token.qtree[start + 1].leny = token.qtree[start].leny;
            token.qtree[start + 2].leny = token.qtree[start].leny;
            token.qtree[start + 3].leny = token.qtree[start].leny;
        } else {
            token.qtree[start].leny = (leny + 1) / 2;
            token.qtree[start + 1].leny = token.qtree[start].leny;
            token.qtree[start + 2].leny = token.qtree[start].leny - 1;
            token.qtree[start + 3].leny = token.qtree[start + 2].leny;
        }

        token.qtree[start + 2].y = y + token.qtree[start].leny;
        token.qtree[start + 3].y = token.qtree[start + 2].y;
    }

    static class WavletTree {
        int x;
        int y;
        int lenx;
        int leny;
        int invrw;
        int invcl;
    }

    static class TableDTT {
        static final float[] HI_FILT_EVEN_8X8_1 = new float[]{0.03226944F, -0.05261415F, -0.18870142F, 0.60328895F, -0.60328895F, 0.18870142F, 0.05261415F, -0.03226944F};
        static final float[] LO_FILT_EVEN_8X8_1 = new float[]{0.07565691F, -0.12335584F, -0.09789297F, 0.8526987F, 0.8526987F, -0.09789297F, -0.12335584F, 0.07565691F};
        static final float[] HI_FILT_NOT_EVEN_8X8_1 = new float[]{0.06453888F, -0.040689416F, -0.41809228F, 0.7884856F, -0.41809228F, -0.040689416F, 0.06453888F};
        static final float[] LO_FILT_NOT_EVEN_8X8_1 = new float[]{0.037828457F, -0.023849465F, -0.1106244F, 0.37740284F, 0.8526987F, 0.37740284F, -0.1106244F, -0.023849465F, 0.037828457F};
        float[] lofilt;
        float[] hifilt;
        int losz;
        int hisz;
        int lodef;
        int hidef;

        TableDTT() {
            this.lofilt = LO_FILT_NOT_EVEN_8X8_1;
            this.hifilt = HI_FILT_NOT_EVEN_8X8_1;
        }
    }

    static class HuffCode {
        int size;
        int code;
    }

    static class HeaderFrm {
        int black;
        int white;
        int width;
        int height;
        float mShift;
        float rScale;
        int wsqEncoder;
        int software;
    }

    static class HuffmanTable {
        int tableLen;
        int bytesLeft;
        int tableId;
        int[] huffbits;
        int[] huffvalues;
    }

    static class TableDHT {
        private static final int MAX_HUFFBITS = 16;
        private static final int MAX_HUFFCOUNTS_WSQ = 256;
        byte tabdef;
        int[] huffbits = new int[16];
        int[] huffvalues = new int[257];
    }

    static class Table_DQT {
        public static final int MAX_SUBBANDS = 64;
        float binCenter;
        float[] qBin = new float[64];
        float[] zBin = new float[64];
        char dqtDef;
    }

    static class QuantTree {
        int x;
        int y;
        int lenx;
        int leny;
    }

    static class Quantization {
        float q;
        float cr;
        float r;
        float[] qbss_t = new float[64];
        float[] qbss = new float[64];
        float[] qzbs = new float[64];
        float[] var = new float[64];
    }

    static class Ref<T> {
        public T value;

        public Ref() {
            this((T) null);
        }

        public Ref(T value) {
            this.value = value;
        }
    }

    static class Token {
        TableDHT[] tableDHT = new TableDHT[8];
        TableDTT tableDTT = new TableDTT();
        Table_DQT tableDQT = new Table_DQT();
        WavletTree[] wtree;
        QuantTree[] qtree;
        Quantization quant_vals = new Quantization();
        List<String> comments = new ArrayList();

        public Token() {
            for(int i = 0; i < 8; ++i) {
                this.tableDHT[i] = new TableDHT();
                this.tableDHT[i].tabdef = 0;
            }

        }
    }
}
