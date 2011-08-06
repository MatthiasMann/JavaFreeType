/*
 * Copyright (c) 2008-2011, Matthias Mann
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
 * Font loading using FreeType2
 * 
 * <p>
 * NOTE: This class is <b>NOT</b> thread safe.
 * </p>
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
    Size initialSize;
    Size activeSize;

    private FreeTypeFont(Pointer library, ByteBuffer file) throws FreeTypeException {
        this.fontBuffer = file;
        this.library = library;
        this.face = FT_New_Memory_Face(library, file, 0);
        this.initialSize = new Size(face.size);
        this.activeSize = initialSize;
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

    public Size getActiveSize() throws IOException {
        ensureOpen();
        return activeSize;
    }

    public void setActiveSize(Size activeSize) throws IOException {
        ensureOpen();
        if(activeSize.getFTF() != this) {
            throw new IllegalArgumentException("Size doesn't belong to this font");
        }
        checkReturnCode(INSTANCE.FT_Activate_Size(activeSize.size));
        this.activeSize = activeSize;
    }
    
    /**
     * Allocates a new {@code Size} object to store a font size.
     * The new {@code Size} is not yet activated.
     * 
     * @return
     * @throws IOException 
     * @see #setActiveSize(de.matthiasmann.javafreetype.FreeTypeFont.Size) 
     * @see #getActiveSize() 
     */
    public Size createNewSize() throws IOException {
        ensureOpen();
        return new Size(FT2Helper.FT_New_Size(face.getPointer()));
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
        // match AWT sign
        if(face.isScalable()) {
            return -round26_6(face.size.metrics.descender);
        } else {
            return -face.descender;
        }
    }

    public int getMaxAscent() throws IOException {
        ensureOpen();
        return roundMaybeScaleY(face.bbox.yMax);
    }

    public int getMaxDescent() throws IOException {
        ensureOpen();
        return -roundMaybeScaleY(face.bbox.yMin);
    }

    public int getLineHeight() throws IOException {
        ensureOpen();
        if(face.isScalable()) {
            return round26_6(face.size.metrics.height);
        } else {
            return face.height;
        }
    }

    public int getLeading() throws IOException {
        ensureOpen();
        int height;
        if(face.isScalable()) {
            height = round26_6(face.size.metrics.height);
        } else {
            height = face.height;
        }
        return height - roundMaybeScaleY(face.bbox.yMax) + roundMaybeScaleY(face.bbox.yMin);
    }

    public int getUnderlinePosition() throws IOException {
        ensureOpen();
        return roundMaybeScaleY(face.underline_position);
    }

    public int getUnderlineThickness() throws IOException {
        ensureOpen();
        return roundMaybeScaleY(face.underline_thickness);
    }

    /**
     * Sets the name (for loadLibrary) to use when loading FreeType natives.
     * Must be called before {@link #isAvailable() }
     * 
     * @param name the library name or null for default
     */
    public static void setNativeLibraryName(String name) {
        nativeLibName = name;
    }

    /**
     * Checks if FreeType natives are available
     * @return true if FreeType natives are available
     */
    public static boolean isAvailable() {
        return FT2Helper.isAvailable();
    }

    public FreeTypeCodePointIterator iterateCodePoints() throws IOException {
        ensureOpen();
        return new FreeTypeCodePointIterator(this);
    }

    public int getGlyphForCodePoint(int codepoint) throws IOException {
        ensureOpen();
        return INSTANCE.FT_Get_Char_Index(face.getPointer(), new NativeLong(codepoint));
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

    /**
     * Loads a glyph using FT_LOAD_RENDER and FT_LOAD_TARGET_NORMAL.
     * 
     * @param glyphIndex the glyph index for this font
     * @return the glyph info
     * @throws IOException if an error occured
     * @see #getGlyphForCodePoint(int) 
     */
    public FreeTypeGlyphInfo loadGlyph(int glyphIndex) throws IOException {
        return loadGlyph(glyphIndex, FT_LOAD_RENDER);
    }
    
    public FreeTypeGlyphInfo loadGlyph(int glyphIndex, LoadTarget target) throws IOException {
        return loadGlyph(glyphIndex, FT_LOAD_RENDER | target.target);
    }
    
    public FreeTypeGlyphInfo loadGlyph(int glyphIndex, int flags) throws IOException {
        ensureOpen();
        checkReturnCode(INSTANCE.FT_Load_Glyph(face.getPointer(), glyphIndex, flags));
        return makeGlyphInfo();
    }

    /**
     * Loads a glyph using FT_LOAD_RENDER and FT_LOAD_TARGET_NORMAL.
     * 
     * @param codepoint the unicode code point to load
     * @return the glyph info
     * @throws IOException if an error occured
     */
    public FreeTypeGlyphInfo loadCodePoint(int codepoint) throws IOException {
        return loadCodePoint(codepoint, FT_LOAD_RENDER);
    }
    
    public FreeTypeGlyphInfo loadCodePoint(int codepoint, LoadTarget target) throws IOException {
        return loadCodePoint(codepoint, FT_LOAD_RENDER | target.target);
    }
    
    public FreeTypeGlyphInfo loadCodePoint(int codepoint, int flags) throws IOException {
        ensureOpen();
        checkReturnCode(INSTANCE.FT_Load_Char(face.getPointer(), new NativeLong(codepoint), flags));
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

    public boolean copyGlyphToByteArray(byte[] dst, int dstOff, int stride) throws IOException {
        ensureGlyphLoaded();
        FT_Bitmap bitmap = face.glyph.bitmap;
        if(bitmap.buffer == null) {
            return false;
        }
        return FT2Helper.copyGlyphToByteArray(bitmap, dst, dstOff, stride);
    }

    public boolean copyGlyphToByteBufferColor(ByteBuffer dst, int stride, byte[] bgColor, byte[] fgColor) throws IOException {
        ensureGlyphLoaded();

        if(bgColor.length != fgColor.length) {
            throw new IllegalArgumentException("color arrays must have same length");
        }
        
        short[] colors = new short[bgColor.length * 2];
        for(int i=0 ; i<bgColor.length ; i++) {
            int bg = bgColor[i] & 255;
            colors[i*2+0] = (short)bg;
            colors[i*2+1] = (short)((fgColor[i] & 255) - bg);
        }

        FT_Bitmap bitmap = face.glyph.bitmap;
        if(bitmap.buffer == null) {
            return false;
        }
        return FT2Helper.copyGlyphToByteBuffer(bitmap, dst, stride, colors);
    }

    /**
     * Loads the TrueType font in the specified {@code ByteBuffer}.
     * <p>
     * NOTE: Do not modify the buffer until all {@code FreeTypeFont} instances are closed.
     * </p>
     * @param font the TrueType font to load
     * @return the FreeTypeFont instance
     * @throws IOException if the font could not be loaded, or if FreeType2 is not available
     * @see #isAvailable() 
     */
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

    private int roundMaybeScaleY(NativeLong value) {
        return roundMaybeScaleY(value.longValue());
    }
    
    private int roundMaybeScaleY(long value) {
        if(face.isScalable()) {
            value = FT_FixMul(value, face.size.metrics.y_scale.longValue());
        }
        return round26_6(value);
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
            initialSize = null;
            activeSize = null;
            checkReturnCode(err);
        }
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        super.finalize();
        close0();
    }
    
    public final class Size {
        final FT_Size size;

        Size(FT_Size size) {
            this.size = size;
        }
        
        FreeTypeFont getFTF() {
            return FreeTypeFont.this;
        }
    }
    
    public enum LoadTarget {
        NORMAL(FT2Library.FT_LOAD_TARGET_NORMAL),
        LIGHT(FT2Library.FT_LOAD_TARGET_LIGHT),
        MONO(FT2Library.FT_LOAD_TARGET_MONO),
        LCD(FT2Library.FT_LOAD_TARGET_LCD),
        LCD_V(FT2Library.FT_LOAD_TARGET_LCD_V);
        
        final int target;
        private LoadTarget(int target) {
            this.target = target;
        }
    }
}
