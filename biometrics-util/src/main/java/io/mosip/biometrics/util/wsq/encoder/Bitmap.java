package io.mosip.biometrics.util.wsq.encoder;

import java.io.Serializable;

public class Bitmap implements Serializable  {
    private static final long serialVersionUID = -8632563339133022850L;
    private int width;
    private int height;
    private int ppi;
    private int depth;
    private int lossyflag;
    private byte[] pixels;
    private int length;

    public Bitmap(byte[] pixels, int width, int height, int ppi, int depth, int lossyflag) {
        this.pixels = pixels;
        this.length = pixels != null ? pixels.length : 0;
        this.width = width;
        this.height = height;
        this.ppi = ppi;
        this.depth = depth;
        this.lossyflag = lossyflag;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getPpi() {
        return this.ppi;
    }

    public byte[] getPixels() {
        return this.pixels;
    }

    public int getLength() {
        return this.length;
    }

    public int getDepth() {
        return this.depth;
    }

    public int getLossyflag() {
        return this.lossyflag;
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("Bitmap [");
        result.append(this.width);
        result.append(" x ");
        result.append(this.height);
        result.append(" x ");
        result.append(this.depth);
        result.append(", ");
        result.append("ppi = ");
        result.append(this.ppi);
        result.append(", ");
        result.append("lossy = ");
        result.append(this.lossyflag);
        result.append("]");
        return result.toString();
    }
}
