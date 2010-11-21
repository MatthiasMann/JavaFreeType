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

import static de.matthiasmann.javafreetype.FT2Library.*;
import static de.matthiasmann.javafreetype.FT2Helper.*;

/**
 *
 * @author Matthias Mann
 */
public class FreeTypeGlyphInfo {

    final int width;
    final int height;
    final int offsetX;
    final int offsetY;
    final int advanceX;
    final int advanceY;

    FreeTypeGlyphInfo(FT_GlyphSlot slot) {
        if(slot.format == FT_GLYPH_FORMAT_BITMAP) {
            this.width   = slot.bitmap.width;
            this.height  = slot.bitmap.rows;
            this.offsetX = slot.bitmap_left;
            this.offsetY = slot.bitmap_top;
        } else {
            this.width   = 0;
            this.height  = 0;
            this.offsetX = 0;
            this.offsetY = 0;
        }
        
        this.advanceX = round26_6(slot.advance.x);
        this.advanceY = round26_6(slot.advance.y);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getAdvanceX() {
        return advanceX;
    }

    public int getAdvanceY() {
        return advanceY;
    }
}
