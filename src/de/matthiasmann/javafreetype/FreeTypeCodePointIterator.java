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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Iterates over all codepoint of a font
 *
 * @author Matthias Mann
 */
public final class FreeTypeCodePointIterator {

    private final FreeTypeFont font;
    private final Pointer face;
    private final IntByReference glyphIndex;
    private NativeLong codePoint;

    FreeTypeCodePointIterator(FreeTypeFont font) {
        this.font = font;
        this.face = font.face.getPointer();
        this.glyphIndex = new IntByReference();
    }

    /**
     * Fetch the next codepoint and glyph index
     *
     * @return true if the iterator has more codepoints.
     * @throws IOException if an error has occured.
     */
    public boolean nextCodePoint() throws IOException {
        font.ensureOpen();
        
        if(codePoint == null) {
            codePoint = FT2Helper.INSTANCE.FT_Get_First_Char(face, glyphIndex);
        } else if(glyphIndex.getValue() == 0) {
            return false;
        } else {
            codePoint = FT2Helper.INSTANCE.FT_Get_Next_Char(face, codePoint, glyphIndex);
        }

        return glyphIndex.getValue() != 0;
    }

    /**
     * Returns the glyph index for the current code point.
     * Different code points may use the same glyph.
     *
     * @return the glyph index.
     */
    public int getGlyphIndex() {
        ensureGlyphIndex();
        return glyphIndex.getValue();
    }

    /**
     * Returns the current unicode codepoint.
     * @return the unicode codepoint
     */
    public int getCodePoint() {
        ensureGlyphIndex();
        return codePoint.intValue();
    }

    private void ensureGlyphIndex() {
        if(glyphIndex.getValue() == 0) {
            throw new NoSuchElementException();
        }
    }
}
