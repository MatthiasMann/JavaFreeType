/*
 * Copyright (c) 2008-2012, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.javafreetype;

import java.awt.image.ComponentSampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static de.matthiasmann.javafreetype.FT2Library.*;

/**
 *
 * @author Matthias Mann
 */
class FT2Helper {

    private static Boolean isAvailable;
    static FT2Library INSTANCE;
    static String nativeLibName;

    static synchronized boolean isAvailable() {
        if(isAvailable == null) {
            try {
                String libName;
                if(nativeLibName != null) {
                    libName = nativeLibName;
                } else if(Platform.isWindows()) {
                    libName = "freetype6";
                } else {
                    libName = "freetype";
                }
                INSTANCE = (FT2Library)Native.loadLibrary(libName, FT2Library.class);
                Pointer library = FT_Init_FreeType();
                try {
                    isAvailable = checkLibrary(library);
                } finally {
                    INSTANCE.FT_Done_FreeType(library);
                }
            } catch (Throwable ex) {
                isAvailable = Boolean.FALSE;
                getLogger().log(Level.SEVERE, "Can't load FreeType2 library", ex);
            }
        }

        return isAvailable;
    }

    static void checkAvailable() {
        if(!isAvailable()) {
            throw new UnsupportedOperationException("FreeType2 library not available");
        }
    }

    static void checkReturnCode(int error) throws FreeTypeException {
        if(error != 0) {
            throw new FreeTypeException(error);
        }
    }

    static String trueTypeEngineToString(int engine) {
        switch(engine) {
            case FT_TRUETYPE_ENGINE_TYPE_NONE:       return "NONE";
            case FT_TRUETYPE_ENGINE_TYPE_UNPATENTED: return "UNPATENTED";
            case FT_TRUETYPE_ENGINE_TYPE_PATENTED:   return "PATENTED";
            default:                                 return "unknown: " + engine;
        }
    }

    static boolean checkLibrary(Pointer library) {
        IntByReference major = new IntByReference();
        IntByReference minor = new IntByReference();
        IntByReference patch = new IntByReference();
        INSTANCE.FT_Library_Version(library, major, minor, patch);

        int engine = -1;
        if(major.getValue() > 2 || (major.getValue() == 2 && minor.getValue() >= 2)) {
            // FT_Get_TrueType_Engine_Type requires FreeType 2.2.x
            engine = INSTANCE.FT_Get_TrueType_Engine_Type(library);
        }

        getLogger().log(Level.INFO, "FreeType2 version: {0}.{1}.{2} TrueType engine: {3}",
                new Object[]{ major.getValue(), minor.getValue(), patch.getValue(), trueTypeEngineToString(engine) });

        final int MIN_MAJOR = 2;
        final int MIN_MINOR = 3;

        if(major.getValue() > MIN_MAJOR) {
            return true;
        } else if(major.getValue() == MIN_MAJOR && minor.getValue() >= MIN_MINOR) {
            return true;
        } else {
            getLogger().log(Level.WARNING, "FreeType2 library too old");
            return false;
        }
    }

    static Pointer FT_Init_FreeType() throws FreeTypeException {
        PointerByReference pp = new PointerByReference();
        checkReturnCode(INSTANCE.FT_Init_FreeType(pp));
        return pp.getValue();
    }

    static FT_Face FT_New_Memory_Face(Pointer library, ByteBuffer buffer, long face_index) throws FreeTypeException {
        PointerByReference pp = new PointerByReference();
        checkReturnCode(INSTANCE.FT_New_Memory_Face(library, buffer,
                new NativeLong(buffer.remaining()), new NativeLong(face_index), pp));
        return new FT_Face(pp.getValue());
    }
    
    static Pointer FT_New_Size(Pointer face) throws FreeTypeException {
        PointerByReference pp = new PointerByReference();
        checkReturnCode(INSTANCE.FT_New_Size(face, pp));
        return pp.getValue();
    }

    static int FT_IMAGE_TAG(int x1, int x2, int x3, int x4) {
        return (x1 << 24) | (x2 << 16) | (x3 << 8) | x4;
    }

    static int to26_6(float value) {
        return Math.round(Math.scalb(value, 6));
    }

    static int round26_6(NativeLong value) {
        return round26_6(value.longValue());
    }
    
    static int round26_6(long value) {
        if(value < 0) {
            return (int)((value - 32) >> 6);
        } else {
            return (int)((value + 32) >> 6);
        }
    }

    static long FT_FixMul(long a, long b) {
        long tmp = a * b;
        if(tmp < 0) {
            tmp -= 0x8000;
        } else {
            tmp += 0x8000;
        }
        return tmp >> 16;
    }
    
    static boolean copyGlyphToBufferedImageGray(FT_Bitmap bitmap, BufferedImage img, int x, int y) {
        if(x + bitmap.width > img.getWidth()) {
            return false;
        }
        if(y + bitmap.rows > img.getHeight()) {
            return false;
        }

        final DataBufferByte dataBuffer = (DataBufferByte)img.getRaster().getDataBuffer();
        final byte[] data = dataBuffer.getData();
        final int stride = ((ComponentSampleModel)img.getSampleModel()).getScanlineStride();

        ByteBuffer bb = bitmap.buffer.getByteBuffer(0, Math.abs(bitmap.pitch) * bitmap.rows);
        int bbOff = (bitmap.pitch < 0) ? (-bitmap.pitch * (bitmap.rows-1)) : 0;
        int dataOff = dataBuffer.getOffset() + y * stride + x;

        for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dataOff+=stride) {
            for(int c=0 ; c<bitmap.width ; c++) {
                data[dataOff + c] = bb.get(bbOff + c);
            }
        }

        return true;
    }

    static boolean copyGlyphToBufferedImageIntARGB(FT_Bitmap bitmap, BufferedImage img, int x, int y, Color color) {
        if(x + bitmap.width > img.getWidth()) {
            return false;
        }
        if(y + bitmap.rows > img.getHeight()) {
            return false;
        }

        final DataBufferInt dataBuffer = (DataBufferInt)img.getRaster().getDataBuffer();
        final int[] data = dataBuffer.getData();
        final int stride = ((SinglePixelPackedSampleModel)img.getSampleModel()).getScanlineStride();

        ByteBuffer bb = bitmap.buffer.getByteBuffer(0, Math.abs(bitmap.pitch) * bitmap.rows);
        int bbOff = (bitmap.pitch < 0) ? (-bitmap.pitch * (bitmap.rows-1)) : 0;
        int dataOff = dataBuffer.getOffset() + y * stride + x;

        int colorValue = (color == null ? Color.WHITE : color).getRGB() & 0xFFFFFF;

        switch(bitmap.pixel_mode) {
            case FT_PIXEL_MODE_GRAY:
                for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dataOff+=stride) {
                    for(int c=0 ; c<bitmap.width ; c++) {
                        data[dataOff + c] = colorValue | (bb.get(bbOff + c) << 24);
                    }
                }
                return true;
                
            case FT_PIXEL_MODE_MONO:
                for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dataOff+=stride) {
                    for(int c=0 ; c<bitmap.width ;) {
                        int value = bb.get(bbOff + c/8);
                        int cnt = Math.min(bitmap.width - c, 8);
                        while(cnt-- > 0) {
                            data[dataOff + c++] = colorValue | ((value & 128) << (24-7))*0xFF;
                            value <<= 1;
                        }
                    }
                }
                return true;
                
            default:
                return false;
        }
    }

    static boolean copyGlyphToByteBuffer(FT_Bitmap bitmap, ByteBuffer dst, int stride) {
        ByteBuffer bb = bitmap.buffer.getByteBuffer(0, Math.abs(bitmap.pitch) * bitmap.rows);
        int bbOff = (bitmap.pitch < 0) ? (-bitmap.pitch * (bitmap.rows-1)) : 0;
        int dstOff = dst.position();

        for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dstOff+=stride) {
            bb.clear().position(bbOff).limit(bbOff + bitmap.width);
            dst.position(dstOff);
            dst.put(bb);
        }

        return true;
    }
    
    static boolean copyGlyphToByteArray(FT_Bitmap bitmap, byte[] dst, int dstOff, int stride) {
        ByteBuffer bb = bitmap.buffer.getByteBuffer(0, Math.abs(bitmap.pitch) * bitmap.rows);
        int bbOff = (bitmap.pitch < 0) ? (-bitmap.pitch * (bitmap.rows-1)) : 0;
        bb.clear();

        switch(bitmap.pixel_mode) {
            case FT_PIXEL_MODE_GRAY:
                for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dstOff+=stride) {
                    bb.position(bbOff);
                    bb.get(dst, dstOff, bitmap.width);
                }
                return true;
                
            case FT_PIXEL_MODE_MONO:
                for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dstOff+=stride) {
                    bb.position(bbOff);
                    for(int c=0 ; c<bitmap.width ;) {
                        int value = bb.get(bbOff + c/8);
                        int cnt = Math.min(bitmap.width - c, 8);
                        while(cnt-- > 0) {
                            dst[dstOff + c++] = (byte)(((value & 128) >> 7)*0xFF);
                            value <<= 1;
                        }
                    }
                }
                return true;
                
            default:
                return false;
        }
    }

    static boolean copyGlyphToByteBuffer(FT_Bitmap bitmap, ByteBuffer dst, int stride, short[] colors) {
        ByteBuffer bb = bitmap.buffer.getByteBuffer(0, Math.abs(bitmap.pitch) * bitmap.rows);
        int bbOff = (bitmap.pitch < 0) ? (-bitmap.pitch * (bitmap.rows-1)) : 0;
        int dstRowOff = dst.position();
        int width = bitmap.width;

        for(int r=0 ; r<bitmap.rows ; r++,bbOff+=bitmap.pitch,dstRowOff+=stride) {
            int dstOff = dstRowOff;
            for(int c=0 ; c<width ; c++) {
                int value = bb.get(bbOff + c) & 255;
                if(value >= 0x80) {
                    value++;
                }
                for(int i=0 ; i<colors.length ; i+=2,dstOff++) {
                    dst.put(dstOff, (byte)(colors[i] + ((colors[i+1] * value) >> 8)));
                }
            }
            dst.position(dstOff);
        }
        
        return true;
    }

    static ByteBuffer inputStreamToByteBuffer(InputStream is) throws IOException {
        final int PAGE_SIZE = 4096;
        final ArrayList<byte[]> pages = new ArrayList<byte[]>();
        for(;;) {
            byte[] page = new byte[PAGE_SIZE];
            int pagePos = 0;
            int read;
            do {
                read = is.read(page, pagePos, PAGE_SIZE-pagePos);
                if(read <= 0) {
                    break;
                }
                pagePos += read;
            } while(pagePos < PAGE_SIZE);

            if(pagePos == PAGE_SIZE) {
                pages.add(page);
            } else {
                ByteBuffer fontBuffer = ByteBuffer.allocateDirect(pages.size() * PAGE_SIZE + pagePos);
                for(int i=0,n=pages.size() ; i<n ; i++) {
                    fontBuffer.put(pages.get(i));
                }
                pages.clear();
                fontBuffer.put(page, 0, pagePos).flip();
                return fontBuffer;
            }
        }
    }

    static Logger getLogger() {
        return Logger.getLogger(FreeTypeFont.class.getName());
    }
}
