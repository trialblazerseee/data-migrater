package io.mosip.biometrics.util.wsq.encoder;

public class WSQConstants {
    int[] BITMASK = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255};
    int MAX_DHT_TABLES = 8;
    int MAX_HUFFBITS = 16;
    int MAX_HUFFCOUNTS_WSQ = 256;
    int MAX_HUFFCOEFF = 74;
    int MAX_HUFFZRUN = 100;
    int MAX_HIFILT = 7;
    int MAX_LOFILT = 9;
    int W_TREELEN = 20;
    int Q_TREELEN = 64;
    int SOI_WSQ = 65440;
    int EOI_WSQ = 65441;
    int SOF_WSQ = 65442;
    int SOB_WSQ = 65443;
    int DTT_WSQ = 65444;
    int DQT_WSQ = 65445;
    int DHT_WSQ = 65446;
    int DRT_WSQ = 65447;
    int COM_WSQ = 65448;
    int STRT_SUBBAND_2 = 19;
    int STRT_SUBBAND_3 = 52;
    int MAX_SUBBANDS = 64;
    int NUM_SUBBANDS = 60;
    int STRT_SUBBAND_DEL = 60;
    int STRT_SIZE_REGION_2 = 4;
    int STRT_SIZE_REGION_3 = 51;
    int COEFF_CODE = 0;
    int RUN_CODE = 1;
    float VARIANCE_THRESH = 1.01F;
    int ANY_WSQ = 65535;
    int TBLS_N_SOF = 2;
    int TBLS_N_SOB = 4;
}
