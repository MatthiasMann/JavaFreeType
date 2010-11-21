/*
 * Copyright (c) 2008-2010, Matthias Mann
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

import java.awt.Color;
import java.awt.image.BufferedImage;
import com.sun.jna.NativeLong;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import com.sun.jna.Pointer;
import java.awt.Point;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;

import static de.matthiasmann.javafreetype.FT2Library.*;
import static de.matthiasmann.javafreetype.FT2Helper.*;

/**
 *
 * @author Matthias Mann
 */
public class FreeTypeFont implements Closeable {

    /**
     * The maximum font file size for {@link #create(java.io.File) }
     */
    public static final int MAX_FONT_FILE_SIZE = 32*1024*1024;

    /** need to keep ByteBuffer alive - it is used by native code */
    ByteBuffer fontBuffer;
    Pointer library;
    FT_Face face;

    private FreeTypeFont(Pointer library, ByteBuffer file) throws FreeTypeException {
        this.fontBuffer = file;
        this.library = library;
        this.face = FT_New_Memory_Face(library, file, 0);
    }

    public void close() throws IOException {
        close0();
    }

    public String getFamilyName() throws IOException {
        ensureOpen();
        return face.family_name;
    }

    public String getStyleName() throws IOException {
        ensureOpen();
        return face.style_name;
    }
    
    public void setCharSize(float width, float height, int horizontalResolution, int verticalResolution) throws IOException {
        ensureOpen();
        checkReturnCode(INSTANCE.FT_Set_Char_Size(face.getPointer(),
                to26_6(width), to26_6(height), horizontalResolution, verticalResolution));
        face.read();
    }

    public void setPixelSize(int width, int height) throws IOException {
        ensureOpen();
        checkReturnCode(INSTANCE.FT_Set_Pixel_Sizes(face.getPointer(), width, height));
        face.read();
    }

    public int getAscent() throws IOException {
        ensureOpen();
        if(face.isScalable()) {
            return round26_6(face.size.metrics.ascender);
        } else {
            return face.ascender;
        }
    }

    public int getDescent() throws IOException {
        ensureOpen();
        if(face.isScalable()) {
            return round26_6(face.size.metrics.descender);
        } else {
            return face.descender;
        }
    }

    public int getMaxAscent() throws IOException {
        ensureOpen();
        return round26_6(face.bbox.yMax);
    }

    public int getMaxDescent() throws IOException {
        ensureOpen();
        return round26_6(face.bbox.yMin);
    }

    public int getLeading() throws IOException {
        ensureOpen();
        if(face.isScalable()) {
            return round26_6(face.size.metrics.height);
        } else {
            return face.height;
        }
    }

    public int getUnderlinePosition() throws IOException {
        ensureOpen();
        return face.underline_position;
    }

    public int getUnderlineThickness() throws IOException {
        ensureOpen();
        return face.underline_thickness;
    }

    public static boolean isAvailable() {
        return FT2Helper.isAvailable();
    }

    public FreeTypeCodePointIterator iterateCodePoints() throws IOException {
        ensureOpen();
        return new FreeTypeCodePointIterator(this);
    }

    public boolean hasKerning() throws IOException {
        ensureOpen();
        return face.hasKerning();
    }

    public Point getKerning(int leftGlyph, int rightGlyph) throws IOException {
        ensureOpen();
        if(face.hasKerning()) {
            FT_Vector vec = new FT_Vector();
            vec.setAutoSynch(false);
            checkReturnCode(INSTANCE.FT_Get_Kerning(face.getPointer(), leftGlyph, rightGlyph, FT_KERNING_DEFAULT, vec));
            vec.read();
            return new Point(round26_6(vec.x), round26_6(vec.y));
        } else {
            return new Point();
        }
    }

    public FreeTypeGlyphInfo loadGlyph(int glyphIndex) throws IOException {
        ensureOpen();
        checkReturnCode(INSTANCE.FT_Load_Glyph(face.getPointer(), glyphIndex, FT_LOAD_RENDER));
        return makeGlyphInfo();
    }

    public FreeTypeGlyphInfo loadCodePoint(int codepoint) throws IOException {
        ensureOpen();
        checkReturnCode(INSTANCE.FT_Load_Char(face.getPointer(), new NativeLong(codepoint), FT_LOAD_RENDER));
        return makeGlyphInfo();
    }

    public boolean copyGlpyhToBufferedImage(BufferedImage img, int x, int y, Color color) throws IOException {
        ensureGlyphLoaded();
        FT_Bitmap bitmap = face.glyph.bitmap;
        if(bitmap.buffer == null) {
            return false;
        }
        switch(img.getType()) {
            case BufferedImage.TYPE_BYTE_GRAY:
                return FT2Helper.copyGlyphToBufferedImageGray(bitmap, img, x, y);
            case BufferedImage.TYPE_INT_ARGB:
                return FT2Helper.copyGlyphToBufferedImageIntARGB(bitmap, img, x, y, color);
            default:
                throw new UnsupportedOperationException("unsupported BufferdImage type");
        }
    }

    public boolean copyGlyphToByteBuffer(ByteBuffer dst, int stride) throws IOException {
        ensureGlyphLoaded();
        FT_Bitmap bitmap = face.glyph.bitmap;
        if(bitmap.buffer == null) {
            return false;
        }
        return FT2Helper.copyGlyphToByteBuffer(bitmap, dst, stride);
    }

    public static FreeTypeFont create(ByteBuffer font) throws IOException {
        FT2Helper.checkAvailable();
        return new FreeTypeFont(FT_Init_FreeType(), font);
    }

    public static FreeTypeFont create(File font) throws IOException {
        FT2Helper.checkAvailable();
        RandomAccessFile raf = new RandomAccessFile(font, "r");
        try {
            int size = (int)Math.min(MAX_FONT_FILE_SIZE, raf.length());
            ByteBuffer fontBuffer = ByteBuffer.allocateDirect(size);
            raf.getChannel().read(fontBuffer);
            fontBuffer.flip();
            return new FreeTypeFont(FT_Init_FreeType(), fontBuffer);
        } finally {
            raf.close();
        }
    }

    public static FreeTypeFont create(InputStream font) throws IOException {
        FT2Helper.checkAvailable();
        ByteBuffer fontBuffer = inputStreamToByteBuffer(font);
        return new FreeTypeFont(FT_Init_FreeType(), fontBuffer);
    }

    private FreeTypeGlyphInfo makeGlyphInfo() {
        face.glyph.read();
        return new FreeTypeGlyphInfo(face.glyph);
    }
    
    final void ensureOpen() throws IOException {
        if(library == null) {
            throw new ClosedChannelException();
        }
    }

    final void ensureGlyphLoaded() throws IOException {
        ensureOpen();
        if(face.glyph == null) {
            throw new IllegalStateException("No glyph loaded");
        }
    }

    private void close0() throws IOException {
        if(library != null) {
            int err = INSTANCE.FT_Done_FreeType(library);
            library = null;
            face = null;
            fontBuffer = null;
            checkReturnCode(err);
        }
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        super.finalize();
        close0();
    }
}
