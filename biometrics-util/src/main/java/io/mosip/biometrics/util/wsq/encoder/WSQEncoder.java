package io.mosip.biometrics.util.wsq.encoder;


import java.io.*;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class WSQEncoder {
    public static void encode(OutputStream os, Bitmap bitmap, double bitRate, String... comments) throws IOException {
        encode((OutputStream)os, bitmap, bitRate, (Map)null, comments);
    }

    public static void encode(DataOutput dataOutput, Bitmap bitmap, double bitRate, String... comments) throws IOException {
        encode((DataOutput)dataOutput, bitmap, bitRate, (Map)null, comments);
    }

    public static void encode(OutputStream os, Bitmap bitmap, double bitRate, Map<String, String> metadata, String... comments) throws IOException {
        encode((DataOutput)(new DataOutputStream(os)), bitmap, bitRate, metadata, comments);
    }

    public static void encode(DataOutput dataOutput, Bitmap _bitmap, double bitRate, Map<String, String> metadata, String... comments) throws IOException {
        BitmapWithMetadata bitmap;
        if (_bitmap instanceof BitmapWithMetadata) {
            bitmap = (BitmapWithMetadata)_bitmap;
        } else {
            bitmap = new BitmapWithMetadata(_bitmap.getPixels(), _bitmap.getWidth(), _bitmap.getHeight(), _bitmap.getPpi(), _bitmap.getDepth(), _bitmap.getLossyflag());
        }

        if (metadata != null) {
            bitmap.getMetadata().putAll(metadata);
        }

        if (comments != null) {
            for(String s : comments) {
                if (s != null) {
                    bitmap.getComments().add(s);
                }
            }
        }

        WSQHelper.Ref<Integer> qsize = new WSQHelper.Ref();
        WSQHelper.Ref<Integer> qsize1 = new WSQHelper.Ref();
        WSQHelper.Ref<Integer> qsize2 = new WSQHelper.Ref();
        WSQHelper.Ref<Integer> qsize3 = new WSQHelper.Ref();
        WSQHelper.Ref<int[]> huffbits = new WSQHelper.Ref();
        WSQHelper.Ref<int[]> huffvalues = new WSQHelper.Ref();
        WSQHelper.Ref<Float> m_shift = new WSQHelper.Ref();
        WSQHelper.Ref<Float> r_scale = new WSQHelper.Ref();
        float[] fdata = convertImageToFloat(bitmap.getPixels(), bitmap.getWidth(), bitmap.getHeight(), m_shift, r_scale);
        WSQHelper.Token token = new WSQHelper.Token();
        WSQHelper.buildWSQTrees(token, bitmap.getWidth(), bitmap.getHeight());
        wsqDecompose(token, fdata, bitmap.getWidth(), bitmap.getHeight(), token.tableDTT.hifilt, 7, token.tableDTT.lofilt, 9);
        token.quant_vals.cr = 0.0F;
        token.quant_vals.q = 0.0F;
        token.quant_vals.r = (float)bitRate;
        variance(token, fdata, bitmap.getWidth(), bitmap.getHeight());
        int[] qdata = quantize(token, qsize, fdata, bitmap.getWidth(), bitmap.getHeight());
        quant_block_sizes(token, qsize1, qsize2, qsize3);
        if ((Integer)qsize.value != (Integer)qsize1.value + (Integer)qsize2.value + (Integer)qsize3.value) {
            throw new IllegalStateException("ERROR : wsq_encode_1 : problem w/quantization block sizes");
        } else {
            dataOutput.writeShort(65440);
            putc_nistcom_wsq(dataOutput, bitmap, (float)bitRate, metadata, comments);
            putc_transform_table(dataOutput, token.tableDTT.lofilt, 9, token.tableDTT.hifilt, 7);
            putc_quantization_table(dataOutput, token);
            putc_frame_header_wsq(dataOutput, bitmap.getWidth(), bitmap.getHeight(), (Float)m_shift.value, (Float)r_scale.value);
            WSQHelper.HuffCode[] hufftable = gen_hufftable_wsq(token, huffbits, huffvalues, qdata, 0, new int[]{(Integer)qsize1.value});
            putc_huffman_table(dataOutput, 65446, 0, (int[])huffbits.value, (int[])huffvalues.value);
            putc_block_header(dataOutput, 0);
            compress_block(dataOutput, qdata, 0, (Integer)qsize1.value, 74, 100, hufftable);
            hufftable = gen_hufftable_wsq(token, huffbits, huffvalues, qdata, (Integer)qsize1.value, new int[]{(Integer)qsize2.value, (Integer)qsize3.value});
            putc_huffman_table(dataOutput, 65446, 1, (int[])huffbits.value, (int[])huffvalues.value);
            putc_block_header(dataOutput, 1);
            compress_block(dataOutput, qdata, (Integer)qsize1.value, (Integer)qsize2.value, 74, 100, hufftable);
            putc_block_header(dataOutput, 1);
            compress_block(dataOutput, qdata, (Integer)qsize1.value + (Integer)qsize2.value, (Integer)qsize3.value, 74, 100, hufftable);
            dataOutput.writeShort(65441);
        }
    }

    private static float[] convertImageToFloat(byte[] data, int width, int height, WSQHelper.Ref<Float> m_shift, WSQHelper.Ref<Float> r_scale) {
        if (data == null) {
            throw new IllegalArgumentException("Image cannot be null");
        } else {
            float[] fip = new float[data.length];
            int sum = 0;
            int low = 255;
            int high = 0;

            for(int cnt = 0; cnt < data.length; ++cnt) {
                if ((data[cnt] & 255) > high) {
                    high = data[cnt] & 255;
                }

                if ((data[cnt] & 255) < low) {
                    low = data[cnt] & 255;
                }

                sum += data[cnt] & 255;
            }

            float mean = (float)sum / (float)data.length;
            m_shift.value = mean;
            float low_diff = (Float)m_shift.value - (float)low;
            float high_diff = (float)high - (Float)m_shift.value;
            if (low_diff >= high_diff) {
                r_scale.value = low_diff;
            } else {
                r_scale.value = high_diff;
            }

            r_scale.value = (Float)r_scale.value / 128.0F;

            for(int var14 = 0; var14 < data.length; ++var14) {
                fip[var14] = ((float)(data[var14] & 255) - (Float)m_shift.value) / (Float)r_scale.value;
            }

            return fip;
        }
    }

    private static void wsqDecompose(WSQHelper.Token token, float[] fdata, int width, int height, float[] hifilt, int hisz, float[] lofilt, int losz) {
        int num_pix = width * height;
        float[] fdata1 = new float[num_pix];

        for(int node = 0; node < token.wtree.length; ++node) {
            int fdataBse = token.wtree[node].y * width + token.wtree[node].x;
            getLets(fdata1, fdata, 0, fdataBse, token.wtree[node].leny, token.wtree[node].lenx, width, 1, hifilt, hisz, lofilt, losz, token.wtree[node].invrw);
            getLets(fdata, fdata1, fdataBse, 0, token.wtree[node].lenx, token.wtree[node].leny, 1, width, hifilt, hisz, lofilt, losz, token.wtree[node].invcl);
        }

    }

    private static void getLets(float[] newdata, float[] olddata, int newIndex, int oldIndex, int len1, int len2, int pitch, int stride, float[] hi, int hsz, float[] lo, int lsz, int inv) {
        if (newdata == null) {
            throw new IllegalArgumentException("newdata == null");
        } else if (olddata == null) {
            throw new IllegalArgumentException("olddata == null");
        } else if (lo == null) {
            throw new IllegalArgumentException("lo == null");
        } else {
            int da_ev = len2 % 2;
            int fi_ev = lsz % 2;
            int loc;
            int hoc;
            int olle;
            int ohle;
            int olre;
            int ohre;
            if (fi_ev != 0) {
                loc = (lsz - 1) / 2;
                hoc = (hsz - 1) / 2 - 1;
                olle = 0;
                ohle = 0;
                olre = 0;
                ohre = 0;
            } else {
                loc = lsz / 2 - 2;
                hoc = hsz / 2 - 2;
                olle = 1;
                ohle = 1;
                olre = 1;
                ohre = 1;
                if (loc == -1) {
                    loc = 0;
                    olle = 0;
                }

                if (hoc == -1) {
                    hoc = 0;
                    ohle = 0;
                }

                for(int i = 0; i < hsz; ++i) {
                    hi[i] = (float)((double)hi[i] * (double)-1.0F);
                }
            }

            int pstr = stride;
            int nstr = -stride;
            int llen;
            int hlen;
            if (da_ev != 0) {
                llen = (len2 + 1) / 2;
                hlen = llen - 1;
            } else {
                llen = len2 / 2;
                hlen = llen;
            }

            for(int rw_cl = 0; rw_cl < len1; ++rw_cl) {
                int lopass;
                int hipass;
                if (inv != 0) {
                    hipass = newIndex + rw_cl * pitch;
                    lopass = hipass + hlen * stride;
                } else {
                    lopass = newIndex + rw_cl * pitch;
                    hipass = lopass + llen * stride;
                }

                int p0 = oldIndex + rw_cl * pitch;
                int p1 = p0 + (len2 - 1) * stride;
                int lspx = p0 + loc * stride;
                int lspxstr = nstr;
                int lle2 = olle;
                int lre2 = olre;
                int hspx = p0 + hoc * stride;
                int hspxstr = nstr;
                int hle2 = ohle;
                int hre2 = ohre;

                for(int pix = 0; pix < hlen; ++pix) {
                    int lpxstr = lspxstr;
                    int lpx = lspx;
                    int lle = lle2;
                    int lre = lre2;
                    newdata[lopass] = olddata[lspx] * lo[0];

                    for(int i = 1; i < lsz; ++i) {
                        if (lpx == p0) {
                            if (lle != 0) {
                                lpxstr = 0;
                                lle = 0;
                            } else {
                                lpxstr = pstr;
                            }
                        }

                        if (lpx == p1) {
                            if (lre != 0) {
                                lpxstr = 0;
                                lre = 0;
                            } else {
                                lpxstr = nstr;
                            }
                        }

                        lpx += lpxstr;
                        newdata[lopass] += olddata[lpx] * lo[i];
                    }

                    lopass += stride;
                    int hpxstr = hspxstr;
                    int hpx = hspx;
                    int hle = hle2;
                    int hre = hre2;
                    newdata[hipass] = olddata[hspx] * hi[0];

                    for(int var49 = 1; var49 < hsz; ++var49) {
                        if (hpx == p0) {
                            if (hle != 0) {
                                hpxstr = 0;
                                hle = 0;
                            } else {
                                hpxstr = pstr;
                            }
                        }

                        if (hpx == p1) {
                            if (hre != 0) {
                                hpxstr = 0;
                                hre = 0;
                            } else {
                                hpxstr = nstr;
                            }
                        }

                        hpx += hpxstr;
                        newdata[hipass] += olddata[hpx] * hi[var49];
                    }

                    hipass += stride;

                    for(int var50 = 0; var50 < 2; ++var50) {
                        if (lspx == p0) {
                            if (lle2 != 0) {
                                lspxstr = 0;
                                lle2 = 0;
                            } else {
                                lspxstr = pstr;
                            }
                        }

                        lspx += lspxstr;
                        if (hspx == p0) {
                            if (hle2 != 0) {
                                hspxstr = 0;
                                hle2 = 0;
                            } else {
                                hspxstr = pstr;
                            }
                        }

                        hspx += hspxstr;
                    }
                }

                if (da_ev != 0) {
                    int lpxstr = lspxstr;
                    int lpx = lspx;
                    int lle = lle2;
                    int lre = lre2;
                    newdata[lopass] = olddata[lspx] * lo[0];

                    for(int i = 1; i < lsz; ++i) {
                        if (lpx == p0) {
                            if (lle != 0) {
                                lpxstr = 0;
                                lle = 0;
                            } else {
                                lpxstr = pstr;
                            }
                        }

                        if (lpx == p1) {
                            if (lre != 0) {
                                lpxstr = 0;
                                lre = 0;
                            } else {
                                lpxstr = nstr;
                            }
                        }

                        lpx += lpxstr;
                        newdata[lopass] += olddata[lpx] * lo[i];
                    }

                    int var10000 = lopass + stride;
                }
            }

            if (fi_ev == 0) {
                for(int i = 0; i < hsz; ++i) {
                    hi[i] = (float)((double)hi[i] * (double)-1.0F);
                }
            }

        }
    }

    private static void variance(WSQHelper.Token token, float[] fip, int width, int height) {
        int lenx = 0;
        int leny = 0;
        float vsum = 0.0F;

        for(int cvr = 0; cvr < 4; ++cvr) {
            int fp = token.qtree[cvr].y * width + token.qtree[cvr].x;
            float ssq = 0.0F;
            float sum_pix = 0.0F;
            int skipx = token.qtree[cvr].lenx / 8;
            int skipy = 9 * token.qtree[cvr].leny / 32;
            lenx = 3 * token.qtree[cvr].lenx / 4;
            leny = 7 * token.qtree[cvr].leny / 16;
            fp += skipy * width + skipx;

            for(int row = 0; row < leny; fp += width - lenx) {
                for(int col = 0; col < lenx; ++col) {
                    sum_pix += fip[fp];
                    ssq += fip[fp] * fip[fp];
                    ++fp;
                }

                ++row;
            }

            float sum2 = sum_pix * sum_pix / (float)(lenx * leny);
            token.quant_vals.var[cvr] = (ssq - sum2) / ((float)(lenx * leny) - 1.0F);
            vsum += token.quant_vals.var[cvr];
        }

        if ((double)vsum < (double)20000.0F) {
            for(int cvr = 0; cvr < 60; ++cvr) {
                int fp = token.qtree[cvr].y * width + token.qtree[cvr].x;
                float ssq = 0.0F;
                float sum_pix = 0.0F;
                lenx = token.qtree[cvr].lenx;
                leny = token.qtree[cvr].leny;

                for(int row = 0; row < leny; fp += width - lenx) {
                    for(int col = 0; col < lenx; ++col) {
                        sum_pix += fip[fp];
                        ssq += fip[fp] * fip[fp];
                        ++fp;
                    }

                    ++row;
                }

                float sum2 = sum_pix * sum_pix / (float)(lenx * leny);
                token.quant_vals.var[cvr] = (float)((double)(ssq - sum2) / ((double)(lenx * leny) - (double)1.0F));
            }
        } else {
            for(int cvr = 4; cvr < 60; ++cvr) {
                int fp = token.qtree[cvr].y * width + token.qtree[cvr].x;
                float ssq = 0.0F;
                float sum_pix = 0.0F;
                int skipx = token.qtree[cvr].lenx / 8;
                int skipy = 9 * token.qtree[cvr].leny / 32;
                lenx = 3 * token.qtree[cvr].lenx / 4;
                leny = 7 * token.qtree[cvr].leny / 16;
                fp += skipy * width + skipx;

                for(int row = 0; row < leny; fp += width - lenx) {
                    for(int col = 0; col < lenx; ++col) {
                        sum_pix += fip[fp];
                        ssq += fip[fp] * fip[fp];
                        ++fp;
                    }

                    ++row;
                }

                float sum2 = sum_pix * sum_pix / (float)(lenx * leny);
                token.quant_vals.var[cvr] = (float)((double)(ssq - sum2) / ((double)(lenx * leny) - (double)1.0F));
            }
        }

    }

    private static int[] quantize(WSQHelper.Token token, WSQHelper.Ref<Integer> qsize, float[] fip, int width, int height) {
        float[] A = new float[60];
        float[] m = new float[60];
        float[] sigma = new float[60];
        int[] K0 = new int[60];
        int[] K1 = new int[60];
        boolean[] NP = new boolean[60];

        for(int cnt = 0; cnt < 52; ++cnt) {
            A[cnt] = 1.0F;
        }

        A[52] = 1.32F;
        A[53] = 1.08F;
        A[54] = 1.42F;
        A[55] = 1.08F;
        A[56] = 1.32F;
        A[57] = 1.42F;
        A[58] = 1.08F;
        A[59] = 1.08F;

        for(int cnt = 0; cnt < 64; ++cnt) {
            token.quant_vals.qbss[cnt] = 0.0F;
            token.quant_vals.qzbs[cnt] = 0.0F;
        }

        for(int cnt = 0; cnt < 60; ++cnt) {
            if (token.quant_vals.var[cnt] < 1.01F) {
                token.quant_vals.qbss[cnt] = 0.0F;
            } else if (cnt < 4) {
                token.quant_vals.qbss[cnt] = 1.0F;
            } else {
                token.quant_vals.qbss[cnt] = 10.0F / (A[cnt] * (float)Math.log((double)token.quant_vals.var[cnt]));
            }
        }

        int[] sip = new int[width * height];
        int sptr = 0;
        float m1 = 9.765625E-4F;
        float m2 = 0.00390625F;
        float m3 = 0.0625F;

        for(int cnt = 0; cnt < 4; ++cnt) {
            m[cnt] = m1;
        }

        for(int cnt = 4; cnt < 51; ++cnt) {
            m[cnt] = m2;
        }

        for(int cnt = 51; cnt < 60; ++cnt) {
            m[cnt] = m3;
        }

        int K0len = 0;

        for(int cnt = 0; cnt < 60; ++cnt) {
            if (token.quant_vals.var[cnt] >= 1.01F) {
                K0[K0len] = cnt;
                K1[K0len++] = cnt;
                sigma[cnt] = (float)Math.sqrt((double)token.quant_vals.var[cnt]);
            }
        }

        int K = 0;
        int Klen = K0len;

        while(true) {
            float S = 0.0F;

            for(int i = 0; i < Klen; ++i) {
                S += m[K1[K + i]];
            }

            float P = 1.0F;

            for(int i = 0; i < Klen; ++i) {
                P = (float)((double)P * Math.pow((double)(sigma[K1[K + i]] / token.quant_vals.qbss[K1[K + i]]), (double)m[K1[K + i]]));
            }

            float q = (float)Math.pow((double)2.0F, (double)(token.quant_vals.r / S - 1.0F)) / 2.5F / (float)Math.pow((double)P, (double)(1.0F / S));
            NP = new boolean[60];
            int NPlen = 0;

            for(int i = 0; i < Klen; ++i) {
                if ((double)(token.quant_vals.qbss[K1[K + i]] / q) >= (double)5.0F * (double)sigma[K1[K + i]]) {
                    NP[K1[K + i]] = true;
                    ++NPlen;
                }
            }

            if (NPlen == 0) {
                int nK = 0;
                Arrays.fill(K1, nK, 60, 0);

                for(int i = 0; i < K0len; ++i) {
                    K1[nK + K0[i]] = 1;
                }

                for(int cnt = 0; cnt < 60; ++cnt) {
                    if (K1[nK + cnt] != 0) {
                        float[] var10000 = token.quant_vals.qbss;
                        var10000[cnt] /= q;
                    } else {
                        token.quant_vals.qbss[cnt] = 0.0F;
                    }

                    token.quant_vals.qzbs[cnt] = 1.2F * token.quant_vals.qbss[cnt];
                }

                for(int cnt = 0; cnt < 60; ++cnt) {
                    int fptr = token.qtree[cnt].y * width + token.qtree[cnt].x;
                    if (token.quant_vals.qbss[cnt] != 0.0F) {
                        float zbin = token.quant_vals.qzbs[cnt] / 2.0F;

                        for(int row = 0; row < token.qtree[cnt].leny; fptr += width - token.qtree[cnt].lenx) {
                            for(int col = 0; col < token.qtree[cnt].lenx; ++col) {
                                if (-zbin <= fip[fptr] && fip[fptr] <= zbin) {
                                    sip[sptr] = 0;
                                } else if (fip[fptr] > 0.0F) {
                                    sip[sptr] = (int)((fip[fptr] - zbin) / token.quant_vals.qbss[cnt] + 1.0F);
                                } else {
                                    sip[sptr] = (int)((fip[fptr] + zbin) / token.quant_vals.qbss[cnt] - 1.0F);
                                }

                                ++sptr;
                                ++fptr;
                            }

                            ++row;
                        }
                    }
                }

                qsize.value = sptr;
                return sip;
            }

            int nK = 0;
            int nKlen = 0;

            for(int i = 0; i < Klen; ++i) {
                if (!NP[K1[K + i]]) {
                    K1[nK + nKlen++] = K1[K + i];
                }
            }

            K = nK;
            Klen = nKlen;
        }
    }

    private static void quant_block_sizes(WSQHelper.Token token, WSQHelper.Ref<Integer> oqsize1, WSQHelper.Ref<Integer> oqsize2, WSQHelper.Ref<Integer> oqsize3) {
        int qsize1 = token.wtree[14].lenx * token.wtree[14].leny;
        int qsize2 = token.wtree[5].leny * token.wtree[1].lenx + token.wtree[4].lenx * token.wtree[4].leny;
        int qsize3 = token.wtree[2].lenx * token.wtree[2].leny + token.wtree[3].lenx * token.wtree[3].leny;

        for(int node = 0; node < 19; ++node) {
            if (token.quant_vals.qbss[node] == 0.0F) {
                qsize1 -= token.qtree[node].lenx * token.qtree[node].leny;
            }
        }

        for(int var8 = 19; var8 < 52; ++var8) {
            if (token.quant_vals.qbss[var8] == 0.0F) {
                qsize2 -= token.qtree[var8].lenx * token.qtree[var8].leny;
            }
        }

        for(int var9 = 52; var9 < 60; ++var9) {
            if (token.quant_vals.qbss[var9] == 0.0F) {
                qsize3 -= token.qtree[var9].lenx * token.qtree[var9].leny;
            }
        }

        oqsize1.value = qsize1;
        oqsize2.value = qsize2;
        oqsize3.value = qsize3;
    }

    private static void putc_huffman_table(DataOutput dataOutput, int marker, int tableId, int[] huffbits, int[] huffvalues) throws IOException {
        dataOutput.writeShort(marker);
        int table_len = 19;
        int values_offset = table_len;

        for(int i = 0; i < 16; ++i) {
            table_len += huffbits[i];
        }

        dataOutput.writeShort(table_len & '\uffff');
        dataOutput.writeByte(tableId & 255);

        for(int i = 0; i < 16; ++i) {
            dataOutput.writeByte(huffbits[i] & 255);
        }

        for(int i = 0; i < table_len - values_offset; ++i) {
            dataOutput.writeByte(huffvalues[i] & 255);
        }

    }

    private static void putc_frame_header_wsq(DataOutput dataOutput, int width, int height, float m_shift, float r_scale) throws IOException {
        dataOutput.writeShort(65442);
        dataOutput.writeShort(17);
        dataOutput.writeByte(0);
        dataOutput.writeByte(255);
        dataOutput.writeShort(height);
        dataOutput.writeShort(width);
        float flt_tmp = m_shift;
        int scale_ex = 0;
        int shrt_dat;
        if ((double)m_shift == (double)0.0F) {
            shrt_dat = 0;
        } else {
            while(flt_tmp < 65535.0F) {
                ++scale_ex;
                flt_tmp *= 10.0F;
            }

            --scale_ex;
            shrt_dat = Math.round(flt_tmp / 10.0F);
        }

        dataOutput.writeByte(scale_ex & 255);
        dataOutput.writeShort(shrt_dat);
        flt_tmp = r_scale;
        scale_ex = 0;
        if ((double)r_scale == (double)0.0F) {
            shrt_dat = 0;
        } else {
            while(flt_tmp < 65535.0F) {
                ++scale_ex;
                flt_tmp *= 10.0F;
            }

            --scale_ex;
            shrt_dat = Math.round(flt_tmp / 10.0F);
        }

        dataOutput.writeByte(scale_ex);
        dataOutput.writeShort(shrt_dat);
        dataOutput.writeByte(0);
        dataOutput.writeShort(0);
    }

    private static void putc_transform_table(DataOutput dataOutput, float[] lofilt, int losz, float[] hifilt, int hisz) throws IOException {
        if (losz >= 0 && losz <= 1073741823) {
            if (hisz >= 0 && hisz <= 1073741823) {
                dataOutput.writeShort(65444);
                dataOutput.writeShort(58);
                dataOutput.writeByte(losz);
                dataOutput.writeByte(hisz);

                for(long coef = (long)(losz >> 1); (coef & 4294967295L) < (long)losz; ++coef) {
                    double dbl_tmp = (double)lofilt[(int)(coef & 4294967295L)];
                    int sign;
                    if (dbl_tmp >= (double)0.0F) {
                        sign = 0;
                    } else {
                        sign = 1;
                        dbl_tmp *= (double)-1.0F;
                    }

                    int scale_ex = 0;
                    long int_dat;
                    if (dbl_tmp == (double)0.0F) {
                        int_dat = 0L;
                    } else {
                        if (!(dbl_tmp < 4.294967295E9)) {
                            dbl_tmp = (double)lofilt[(int)(coef & 4294967295L)];
                            throw new IllegalStateException("ERROR: putc_transform_table : lofilt[%d] to high at %f");
                        }

                        while(dbl_tmp < 4.294967295E9) {
                            ++scale_ex;
                            dbl_tmp *= (double)10.0F;
                        }

                        --scale_ex;
                        int_dat = (long)((int)Math.round(dbl_tmp / (double)10.0F));
                    }

                    dataOutput.writeByte(sign & 255);
                    dataOutput.writeByte(scale_ex & 255);
                    dataOutput.writeInt((int)(int_dat & 4294967295L));
                }

                for(long var13 = (long)(hisz >> 1); (long)((int)(var13 & 4294967295L)) < (long)hisz; ++var13) {
                    double dbl_tmp = (double)hifilt[(int)(var13 & 4294967295L)];
                    int sign;
                    if (dbl_tmp >= (double)0.0F) {
                        sign = 0;
                    } else {
                        sign = 1;
                        dbl_tmp *= (double)-1.0F;
                    }

                    int scale_ex = 0;
                    long int_dat;
                    if (dbl_tmp == (double)0.0F) {
                        int_dat = 0L;
                    } else {
                        if (!(dbl_tmp < 4.294967295E9)) {
                            dbl_tmp = (double)hifilt[(int)(var13 & 4294967295L)];
                            throw new IllegalStateException("ERROR: putc_transform_table : hifilt[" + var13 + "] to high at " + dbl_tmp);
                        }

                        while(dbl_tmp < 4.294967295E9) {
                            ++scale_ex;
                            dbl_tmp *= (double)10.0F;
                        }

                        --scale_ex;
                        int_dat = (long)((int)Math.round(dbl_tmp / (double)10.0F));
                    }

                    dataOutput.writeByte(sign & 255);
                    dataOutput.writeByte(scale_ex & 255);
                    dataOutput.writeInt((int)(int_dat & 4294967295L));
                }

            } else {
                throw new IllegalStateException("Writing transform table: hisz out of range");
            }
        } else {
            throw new IllegalStateException("Writing transform table: losz out of range");
        }
    }

    private static void putc_quantization_table(DataOutput dataOutput, WSQHelper.Token token) throws IOException {
        dataOutput.writeShort(65445);
        dataOutput.writeShort(389);
        dataOutput.writeByte(2);
        dataOutput.writeShort(44);

        for(int sub = 0; sub < 64; ++sub) {
            int shrt_dat;
            int shrt_dat2;
            int scale_ex;
            int scale_ex2;
            if (sub >= 0 && sub < 60) {
                if (token.quant_vals.qbss[sub] == 0.0F) {
                    scale_ex = 0;
                    scale_ex2 = 0;
                    shrt_dat = 0;
                    shrt_dat2 = 0;
                } else {
                    float flt_tmp = token.quant_vals.qbss[sub];
                    scale_ex = 0;
                    if (!(flt_tmp < 65535.0F)) {
                        float var11 = token.quant_vals.qbss[sub];
                        throw new IllegalStateException("ERROR : putc_quantization_table : Q[%d] to high at %f");
                    }

                    while(flt_tmp < 65535.0F) {
                        ++scale_ex;
                        flt_tmp *= 10.0F;
                    }

                    --scale_ex;
                    shrt_dat = (int)Math.round((double)flt_tmp / (double)10.0F);
                    flt_tmp = token.quant_vals.qzbs[sub];
                    scale_ex2 = 0;
                    if (!(flt_tmp < 65535.0F)) {
                        float var10000 = token.quant_vals.qzbs[sub];
                        throw new IllegalArgumentException("ERROR : putc_quantization_table : Z[%d] to high at %f");
                    }

                    while(flt_tmp < 65535.0F) {
                        ++scale_ex2;
                        flt_tmp *= 10.0F;
                    }

                    --scale_ex2;
                    shrt_dat2 = (int)Math.round((double)flt_tmp / (double)10.0F);
                }
            } else {
                scale_ex = 0;
                scale_ex2 = 0;
                shrt_dat = 0;
                shrt_dat2 = 0;
            }

            dataOutput.writeByte(scale_ex & 255);
            dataOutput.writeShort(shrt_dat & '\uffff');
            dataOutput.writeByte(scale_ex2 & 255);
            dataOutput.writeShort(shrt_dat2 & '\uffff');
        }

    }

    private static void putc_block_header(DataOutput dataOutput, int table) throws IOException {
        dataOutput.writeShort(65443);
        dataOutput.writeShort(3);
        dataOutput.writeByte(table & 255);
    }

    private static void putc_nistcom_wsq(DataOutput dataOutput, Bitmap bitmap, float r_bitrate, Map<String, String> metadata, String[] comments) throws IOException {
        Map<String, String> nistcom = new LinkedHashMap();
        nistcom.put("NIST_COM", "---");
        nistcom.put("PIX_WIDTH", "---");
        nistcom.put("PIX_HEIGHT", "---");
        nistcom.put("PIX_DEPTH", "---");
        nistcom.put("PPI", "---");
        nistcom.put("LOSSY", "---");
        nistcom.put("COLORSPACE", "---");
        nistcom.put("COMPRESSION", "---");
        nistcom.put("WSQ_BITRATE", "---");
        if (metadata != null) {
            nistcom.putAll(metadata);
        }

        nistcom.put("NIST_COM", Integer.toString(nistcom.size()));
        nistcom.put("PIX_WIDTH", Integer.toString(bitmap.getWidth()));
        nistcom.put("PIX_HEIGHT", Integer.toString(bitmap.getHeight()));
        nistcom.put("PPI", Integer.toString(bitmap.getPpi()));
        nistcom.put("PIX_DEPTH", "8");
        nistcom.put("LOSSY", "1");
        nistcom.put("COLORSPACE", "GRAY");
        nistcom.put("COMPRESSION", "WSQ");
        nistcom.put("WSQ_BITRATE", Float.toString(r_bitrate));
        putc_comment(dataOutput, 65448, fetToString(nistcom));
        if (comments != null) {
            for(String s : comments) {
                if (s != null) {
                    putc_comment(dataOutput, 65448, s);
                }
            }
        }

    }

    private static void putc_comment(DataOutput dataOutput, int marker, String comment) throws IOException {
        dataOutput.writeShort(marker);
        int hdr_size = 2 + comment.length();
        dataOutput.writeShort(hdr_size & '\uffff');
        dataOutput.write(comment.getBytes());
    }

    private static WSQHelper.HuffCode[] gen_hufftable_wsq(WSQHelper.Token token, WSQHelper.Ref<int[]> ohuffbits, WSQHelper.Ref<int[]> ohuffvalues, int[] sip, int offset, int[] block_sizes) {
        WSQHelper.Ref<Integer> last_size = new WSQHelper.Ref();
        int[] huffcounts = count_block(256, sip, offset, block_sizes[0], 74, 100);

        for(int i = 1; i < block_sizes.length; ++i) {
            int[] huffcounts2 = count_block(256, sip, offset + block_sizes[i - 1], block_sizes[i], 74, 100);

            for(int j = 0; j < 256; ++j) {
                huffcounts[j] += huffcounts2[j];
            }
        }

        int[] codesize = find_huff_sizes(huffcounts, 256);
        WSQHelper.Ref<Boolean> adjust = new WSQHelper.Ref();
        int[] huffbits = find_num_huff_sizes(adjust, codesize, 256);
        if ((Boolean)adjust.value) {
            sort_huffbits(huffbits);
        }

        int[] huffvalues = sort_code_sizes(codesize, 256);
        WSQHelper.HuffCode[] hufftable1 = build_huffsizes(last_size, huffbits, 256);
        build_huffcodes(hufftable1);
        check_huffcodes_wsq(hufftable1, (Integer)last_size.value);
        WSQHelper.HuffCode[] hufftable2 = build_huffcode_table(hufftable1, (Integer)last_size.value, huffvalues, 256);
        ohuffbits.value = huffbits;
        ohuffvalues.value = huffvalues;
        return hufftable2;
    }

    private static int[] count_block(int max_huffcounts, int[] sip, int sip_offset, int sip_siz, int MaxCoeff, int MaxZRun) {
        int rcnt = 0;
        if (MaxCoeff >= 0 && MaxCoeff <= 65535) {
            if (MaxZRun >= 0 && MaxZRun <= 65535) {
                int[] counts = new int[max_huffcounts + 1];
                counts[max_huffcounts] = 1;
                int LoMaxCoeff = 1 - MaxCoeff;
                int state = 0;

                for(int cnt = sip_offset; cnt < sip_siz; ++cnt) {
                    int pix = sip[cnt];
                    switch (state) {
                        case 0:
                            if (pix == 0) {
                                state = 1;
                                rcnt = 1;
                            } else if (pix > MaxCoeff) {
                                if (pix > 255) {
                                    int var18 = counts[103]++;
                                } else {
                                    int var19 = counts[101]++;
                                }
                            } else if (pix < LoMaxCoeff) {
                                if (pix < -255) {
                                    int var20 = counts[104]++;
                                } else {
                                    int var21 = counts[102]++;
                                }
                            } else {
                                ++counts[pix + 180];
                            }
                            break;
                        case 1:
                            if (pix == 0 && rcnt < 65535) {
                                ++rcnt;
                            } else {
                                if (rcnt <= MaxZRun) {
                                    int var10002 = counts[rcnt]++;
                                } else if (rcnt <= 255) {
                                    int var12 = counts[105]++;
                                } else {
                                    if (rcnt > 65535) {
                                        throw new IllegalStateException("ERROR: count_block : Zrun to long in count block.");
                                    }

                                    int var13 = counts[106]++;
                                }

                                if (pix != 0) {
                                    if (pix > MaxCoeff) {
                                        if (pix > 255) {
                                            int var14 = counts[103]++;
                                        } else {
                                            int var15 = counts[101]++;
                                        }
                                    } else if (pix < LoMaxCoeff) {
                                        if (pix < -255) {
                                            int var16 = counts[104]++;
                                        } else {
                                            int var17 = counts[102]++;
                                        }
                                    } else {
                                        ++counts[pix + 180];
                                    }

                                    state = 0;
                                } else {
                                    rcnt = 1;
                                    state = 1;
                                }
                            }
                    }
                }

                if (state == 1) {
                    if (rcnt <= MaxZRun) {
                        int var22 = counts[rcnt]++;
                    } else if (rcnt <= 255) {
                        int var23 = counts[105]++;
                    } else {
                        if (rcnt > 65535) {
                            throw new IllegalStateException("ERROR: count_block : Zrun to long in count block.");
                        }

                        int var24 = counts[106]++;
                    }
                }

                return counts;
            } else {
                throw new IllegalStateException("ERROR : compress_block : MaxZRun out of range.");
            }
        } else {
            throw new IllegalStateException("ERROR : compress_block : MaxCoeff out of range.");
        }
    }

    private static int[] find_num_huff_sizes(WSQHelper.Ref<Boolean> adjust, int[] codesize, int max_huffcounts) {
        adjust.value = false;
        int[] bits = new int[32];

        for(int i = 0; i < max_huffcounts; ++i) {
            if (codesize[i] != 0) {
                ++bits[codesize[i] - 1];
            }

            if (codesize[i] > 16) {
                adjust.value = true;
            }
        }

        return bits;
    }

    private static int[] sort_code_sizes(int[] codesize, int max_huffcounts) {
        int[] values = new int[max_huffcounts + 1];
        int i2 = 0;

        for(int i = 1; i <= 32; ++i) {
            for(int i3 = 0; i3 < max_huffcounts; ++i3) {
                if (codesize[i3] == i) {
                    values[i2] = i3;
                    ++i2;
                }
            }
        }

        return values;
    }

    private static WSQHelper.HuffCode[] build_huffsizes(WSQHelper.Ref<Integer> temp_size, int[] huffbits, int max_huffcounts) {
        int number_of_codes = 1;
        WSQHelper.HuffCode[] huffcode_table = new WSQHelper.HuffCode[max_huffcounts + 1];

        for(int i = 0; i < huffcode_table.length; ++i) {
            huffcode_table[i] = new WSQHelper.HuffCode();
        }

        temp_size.value = 0;

        for(int code_size = 1; code_size <= 16; ++code_size) {
            while(number_of_codes <= huffbits[code_size - 1]) {
                huffcode_table[(Integer)temp_size.value].size = code_size;
                Integer var7 = (Integer)temp_size.value;
                Object var8 = temp_size.value = (Integer)temp_size.value + 1;
                ++number_of_codes;
            }

            number_of_codes = 1;
        }

        huffcode_table[(Integer)temp_size.value].size = 0;
        return huffcode_table;
    }

    private static int[] find_huff_sizes(int[] freq, int max_huffcounts) {
        int[] codesize = new int[max_huffcounts + 1];
        int[] others = new int[max_huffcounts + 1];

        for(int i = 0; i <= max_huffcounts; ++i) {
            others[i] = -1;
        }

        while(true) {
            int[] values = find_least_freq(freq, max_huffcounts);
            int value1 = values[0];
            int value2 = values[1];
            if (value2 == -1) {
                return codesize;
            }

            freq[value1] += freq[value2];
            freq[value2] = 0;

            int var8;
            for(int var10002 = codesize[value1]++; others[value1] != -1; var8 = codesize[value1]++) {
                value1 = others[value1];
            }

            others[value1] = value2;

            for(int var9 = codesize[value2]++; others[value2] != -1; var8 = codesize[value2]++) {
                value2 = others[value2];
            }
        }
    }

    private static int[] find_least_freq(int[] freq, int max_huffcounts) {
        int code2 = Integer.MAX_VALUE;
        int code1 = Integer.MAX_VALUE;
        int set = 1;
        int value1 = -1;
        int value2 = -1;

        for(int i = 0; i <= max_huffcounts; ++i) {
            if (freq[i] != 0) {
                if (set == 1) {
                    code1 = freq[i];
                    value1 = i;
                    ++set;
                } else {
                    if (set == 2) {
                        code2 = freq[i];
                        value2 = i;
                        ++set;
                    }

                    int code_temp = freq[i];
                    if (code1 >= code_temp || code2 >= code_temp) {
                        if (code_temp >= code1 && (code_temp != code1 || i <= value1)) {
                            if (code_temp < code2 || code_temp == code2 && i > value2) {
                                code2 = code_temp;
                                value2 = i;
                            }
                        } else {
                            code2 = code1;
                            value2 = value1;
                            code1 = code_temp;
                            value1 = i;
                        }
                    }
                }
            }
        }

        return new int[]{value1, value2};
    }

    private static void sort_huffbits(int[] bits) {
        int l3 = 32;
        int l1 = l3 - 1;
        int l2 = 15;
        int[] tbits = new int[l3];

        for(int i = 0; i < 32; ++i) {
            tbits[i] = bits[i];
        }

        int var7;
        for(var7 = l1; var7 > l2; --var7) {
            while(tbits[var7] > 0) {
                int j;
                for(j = var7 - 2; tbits[j] == 0; --j) {
                }

                tbits[var7] -= 2;
                ++tbits[var7 - 1];
                tbits[j + 1] += 2;
                int var10002 = tbits[j]--;
            }

            tbits[var7] = 0;
        }

        while(tbits[var7] == 0) {
            --var7;
        }

        int var10 = tbits[var7]--;

        for(int var8 = 0; var8 < 32; ++var8) {
            bits[var8] = (byte)tbits[var8];
        }

        for(int var9 = 16; var9 < l3; ++var9) {
            if (bits[var9] > 0) {
                throw new IllegalStateException("ERROR : sort_huffbits : Code length of %d is greater than 16.");
            }
        }

    }

    private static void build_huffcodes(WSQHelper.HuffCode[] huffcode_table) {
        int pointer = 0;
        int temp_code = 0;
        int temp_size = huffcode_table[0].size;

        while(true) {
            huffcode_table[pointer].code = temp_code++;
            ++pointer;
            if (huffcode_table[pointer].size != temp_size) {
                if (huffcode_table[pointer].size == 0) {
                    return;
                }

                do {
                    temp_code <<= 1;
                    ++temp_size;
                } while(huffcode_table[pointer].size != temp_size);

                if (huffcode_table[pointer].size != temp_size) {
                    return;
                }
            }
        }
    }

    private static void check_huffcodes_wsq(WSQHelper.HuffCode[] hufftable, int last_size) {
        for(int i = 0; i < last_size; ++i) {
            boolean all_ones = true;

            for(int k = 0; k < hufftable[i].size && all_ones; ++k) {
                all_ones = all_ones && (hufftable[i].code >> k & 1) != 0;
            }

            if (all_ones) {
                throw new IllegalStateException("WARNING: A code in the hufftable contains an all 1's code. This image may still be decodable. It is not compliant with the WSQ specification.");
            }
        }

    }

    private static WSQHelper.HuffCode[] build_huffcode_table(WSQHelper.HuffCode[] in_huffcode_table, int last_size, int[] values, int max_huffcounts) {
        WSQHelper.HuffCode[] new_huffcode_table = new WSQHelper.HuffCode[max_huffcounts + 1];

        for(int i = 0; i < new_huffcode_table.length; ++i) {
            new_huffcode_table[i] = new WSQHelper.HuffCode();
        }

        for(int size = 0; size < last_size; ++size) {
            new_huffcode_table[values[size]].code = in_huffcode_table[size].code;
            new_huffcode_table[values[size]].size = in_huffcode_table[size].size;
        }

        return new_huffcode_table;
    }

    private static void compress_block(DataOutput dataOutput, int[] sip, int offset, int length, int MaxCoeff, int MaxZRun, WSQHelper.HuffCode[] codes) throws IOException {
        int rcnt = 0;
        if (MaxCoeff >= 0 && MaxCoeff <= 65535) {
            if (MaxZRun >= 0 && MaxZRun <= 65535) {
                int LoMaxCoeff = 1 - MaxCoeff;
                WSQHelper.Ref<Integer> outbit = new WSQHelper.Ref(7);
                WSQHelper.Ref<Integer> bytes = new WSQHelper.Ref(0);
                WSQHelper.Ref<Integer> bits = new WSQHelper.Ref(0);
                int state = 0;

                for(int cnt = offset; cnt < length; ++cnt) {
                    int pix = sip[cnt];
                    switch (state) {
                        case 0:
                            if (pix == 0) {
                                state = 1;
                                rcnt = 1;
                            } else if (pix > MaxCoeff) {
                                if (pix > 255) {
                                    write_bits(dataOutput, codes[103].size, codes[103].code, outbit, bits, bytes);
                                    write_bits(dataOutput, 16, pix, outbit, bits, bytes);
                                } else {
                                    write_bits(dataOutput, codes[101].size, codes[101].code, outbit, bits, bytes);
                                    write_bits(dataOutput, 8, pix, outbit, bits, bytes);
                                }
                            } else if (pix < LoMaxCoeff) {
                                if (pix < -255) {
                                    write_bits(dataOutput, codes[104].size, codes[104].code, outbit, bits, bytes);
                                    write_bits(dataOutput, 16, -pix, outbit, bits, bytes);
                                } else {
                                    write_bits(dataOutput, codes[102].size, codes[102].code, outbit, bits, bytes);
                                    write_bits(dataOutput, 8, -pix, outbit, bits, bytes);
                                }
                            } else {
                                write_bits(dataOutput, codes[pix + 180].size, codes[pix + 180].code, outbit, bits, bytes);
                            }
                            break;
                        case 1:
                            if (pix == 0 && rcnt < 65535) {
                                ++rcnt;
                            } else {
                                if (rcnt <= MaxZRun) {
                                    write_bits(dataOutput, codes[rcnt].size, codes[rcnt].code, outbit, bits, bytes);
                                } else if (rcnt <= 255) {
                                    write_bits(dataOutput, codes[105].size, codes[105].code, outbit, bits, bytes);
                                    write_bits(dataOutput, 8, rcnt, outbit, bits, bytes);
                                } else {
                                    if (rcnt > 65535) {
                                        throw new IllegalStateException("ERROR : compress_block : zrun too large.");
                                    }

                                    write_bits(dataOutput, codes[106].size, codes[106].code, outbit, bits, bytes);
                                    write_bits(dataOutput, 16, rcnt, outbit, bits, bytes);
                                }

                                if (pix != 0) {
                                    if (pix > MaxCoeff) {
                                        if (pix > 255) {
                                            write_bits(dataOutput, codes[103].size, codes[103].code, outbit, bits, bytes);
                                            write_bits(dataOutput, 16, pix, outbit, bits, bytes);
                                        } else {
                                            write_bits(dataOutput, codes[101].size, codes[101].code, outbit, bits, bytes);
                                            write_bits(dataOutput, 8, pix, outbit, bits, bytes);
                                        }
                                    } else if (pix < LoMaxCoeff) {
                                        if (pix < -255) {
                                            write_bits(dataOutput, codes[104].size, codes[104].code, outbit, bits, bytes);
                                            write_bits(dataOutput, 16, -pix, outbit, bits, bytes);
                                        } else {
                                            write_bits(dataOutput, codes[102].size, codes[102].code, outbit, bits, bytes);
                                            write_bits(dataOutput, 8, -pix, outbit, bits, bytes);
                                        }
                                    } else {
                                        write_bits(dataOutput, codes[pix + 180].size, codes[pix + 180].code, outbit, bits, bytes);
                                    }

                                    state = 0;
                                } else {
                                    rcnt = 1;
                                    state = 1;
                                }
                            }
                    }
                }

                if (state == 1) {
                    if (rcnt <= MaxZRun) {
                        write_bits(dataOutput, codes[rcnt].size, codes[rcnt].code, outbit, bits, bytes);
                    } else if (rcnt <= 255) {
                        write_bits(dataOutput, codes[105].size, codes[105].code, outbit, bits, bytes);
                        write_bits(dataOutput, 8, rcnt, outbit, bits, bytes);
                    } else {
                        if (rcnt > 65535) {
                            throw new IllegalStateException("ERROR : compress_block : zrun2 too large.");
                        }

                        write_bits(dataOutput, codes[106].size, codes[106].code, outbit, bits, bytes);
                        write_bits(dataOutput, 16, rcnt, outbit, bits, bytes);
                    }
                }

                flush_bits(dataOutput, outbit, bits, bytes);
            } else {
                throw new IllegalStateException("ERROR : compress_block : MaxZRun out of range.");
            }
        } else {
            throw new IllegalStateException("ERROR : compress_block : MaxCoeff out of range.");
        }
    }

    private static String fetToString(Map<String, String> fet) {
        try {
            StringBuffer result = new StringBuffer();

            for(Map.Entry<String, String> entry : fet.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String key = URLEncoder.encode((String)entry.getKey(), "UTF-8");
                    String value = URLEncoder.encode((String)entry.getValue(), "UTF-8");
                    result.append(key);
                    result.append(" ");
                    result.append(value);
                    result.append("\n");
                }
            }

            return result.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static void write_bits(DataOutput outbuf, int size, int code, WSQHelper.Ref<Integer> outbit, WSQHelper.Ref<Integer> bits, WSQHelper.Ref<Integer> bytes) throws IOException {
        for(int num = size - 1; num >= 0; --num) {
            bits.value = (Integer)bits.value << 1;
            bits.value = (Integer)bits.value | code >> num & 1 & 255;
            if ((outbit.value = (Integer)outbit.value - 1) < 0) {
                outbuf.write((Integer)bits.value);
                if (((Integer)bits.value & 255) == 255) {
                    outbuf.write(0);
                    Integer var8 = (Integer)bytes.value;
                    Object var9 = bytes.value = (Integer)bytes.value + 1;
                }

                Integer var10 = (Integer)bytes.value;
                Object var11 = bytes.value = (Integer)bytes.value + 1;
                outbit.value = 7;
                bits.value = 0;
            }
        }

    }

    private static void flush_bits(DataOutput outbuf, WSQHelper.Ref<Integer> outbit, WSQHelper.Ref<Integer> bits, WSQHelper.Ref<Integer> bytes) throws IOException {
        if ((Integer)outbit.value != 7) {
            for(int cnt = (Integer)outbit.value; cnt >= 0; --cnt) {
                bits.value = (Integer)bits.value << 1;
                bits.value = (Integer)bits.value | 1;
            }

            outbuf.write((Integer)bits.value);
            if ((Integer)bits.value == 255) {
                bits.value = 0;
                outbuf.write(0);
                Integer var6 = (Integer)bytes.value;
                Object var7 = bytes.value = (Integer)bytes.value + 1;
            }

            Integer var8 = (Integer)bytes.value;
            Object var9 = bytes.value = (Integer)bytes.value + 1;
            outbit.value = 7;
            bits.value = 0;
        }

    }
}
