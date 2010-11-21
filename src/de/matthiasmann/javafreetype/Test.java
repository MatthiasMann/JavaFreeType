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
import java.io.File;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 *
 * @author Matthias Mann
 */
public class Test {

    @SuppressWarnings("CallToThreadDumpStack")
    public static void main(String[] args) throws IOException {
        if(args.length == 0) {
            System.err.println("Usage: java de.matthiasmann.javafreetype.Test <font-file>");
            System.exit(1);
        }

        FreeTypeFont font = FreeTypeFont.create(new File(args[0]));
        try {
            font.setCharSize(0, 14.0f, 72, 72);

            BufferedImage img = new BufferedImage(1024, 512, BufferedImage.TYPE_BYTE_GRAY);
            int imgX = 0;
            int imgY = 0;
            int imgRowHeight = 0;

            FreeTypeCodePointIterator iter = font.iterateCodePoints();
            while(iter.nextCodePoint()) {
                int glyphIndex = iter.getGlyphIndex();
                int charCode = iter.getCodePoint();

                FreeTypeGlyphInfo info = font.loadGlyph(glyphIndex);
                if(info != null) {
                    System.out.printf("%4d = %04X (%c) - w:%d h:%d x:%d y:%d adv:%d\n",
                            glyphIndex, charCode, (charCode < ' ') ? ' ' : charCode,
                            info.getWidth(), info.getHeight(), info.getOffsetX(),
                            info.getOffsetY(), info.getAdvanceX());

                    if(imgX + info.getWidth() > img.getWidth()) {
                        imgX = 0;
                        imgY += imgRowHeight;
                        imgRowHeight = 0;
                    }

                    if(font.copyGlpyhToBufferedImage(img, imgX, imgY, Color.WHITE)) {
                        imgRowHeight = Math.max(imgRowHeight, info.getHeight());
                        imgX += info.getWidth();
                    }
                }
            }
            
            JOptionPane.showMessageDialog(null, new ImageIcon(img));
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            font.close();
        }
    }
}
