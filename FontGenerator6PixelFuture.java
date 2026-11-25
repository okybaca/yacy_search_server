/**
 *  FontGenerator6PixelFuture
 *  Copyright 2025 by Michael Christen
 *  First released 23.09.2025 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.visualization;

/**
 * A 6x7 pixel font with angled, circuit-inspired strokes that lean into
 * futuristic HUD motifs while keeping the same ASCII coverage as the other
 * generator variants.
 */
public class FontGenerator6PixelFuture { 

    /**
     * font: a character matrix of 96 characters:
     * 
     *  0x20: !"#$%&'
     *  0x28:()*+,-./
     *  0x30:01234567
     *  0x38:89:;<=>?
     *  0x40:@ABCDEFG
     *  0x48:HIJKLMNO
     *  0x50:PQRSTUVW
     *  0x58:XYZ[\]^_
     *  0x60:`abcdefg
     *  0x68:hijklmno
     *  0x70:pqrstuvw
     *  0x78:xyz{|}~
     */
    public static final String[] font_light = {
        "......",  "..X...",  ".X..X.",  ".X..X.",  "...X...",  "XX...X",  "..X...",  "...X..",  
        "......",  "..X...",  ".X..X.",  "XXXXXX",  "XXXXXXX",  "XX..X.",  ".X.X..",  "..X...",  
        "......",  "..X...",  "......",  ".X..X.",  "X..X...",  "...X..",  "..X...",  "......",  
        "......",  "..X...",  "......",  ".X..X.",  "XXXXXXX",  "..X...",  ".X.X.X",  "......",  
        "......",  "......",  "......",  "XXXXXX",  "...X..X",  ".X..XX",  "X...X.",  "......",  
        "......",  "..X...",  "......",  ".X..X.",  "XXXXXXX",  "X...XX",  ".XXX.X",  "......",  
        "......",  "......",  "......",  "......",  "...X...",  "......",  "......",  "......",  
    
        "...X..",  "..X...",  "......",  "......",  "......",  "......",  "......",  "......",  
        "..X...",  "...X..",  "..X.X.",  "...X..",  "......",  "......",  "......",  "....X.",  
        "..X...",  "...X..",  "...X..",  "...X..",  "......",  "......",  "......",  "...X..",  
        "..X...",  "...X..",  ".XXXXX",  ".XXXXX",  "......",  ".XXXXX",  "......",  "..X...",  
        "..X...",  "...X..",  "...X..",  "...X..",  "..X...",  "......",  "..XX..",  ".X....",  
        "...X..",  "..X...",  "..X.X.",  "...X..",  "..X...",  "......",  "..XX..",  "X.....",  
        "......",  "......",  "......",  "......",  ".X....",  "......",  "......",  "......",  
    
        ".XXXX.",  "..XX..",  ".XXXX.",  ".XXXX.",  "...X..",  "XXXXXX",  ".XXXX.",  "XXXXXX",  
        "X...XX",  ".X.X..",  "X....X",  "X....X",  "..XX..",  "X.....",  "X.....",  ".....X",  
        "X..X.X",  "...X..",  ".....X",  "...XX.",  ".X.X..",  "XXXXX.",  "XXXXX.",  "....X.",  
        "X.X..X",  "...X..",  ".XXXX.",  ".....X",  "X..X..",  ".....X",  "X....X",  "...X..",  
        "XX...X",  "...X..",  "X.....",  "X....X",  "XXXXXX",  "X....X",  "X....X",  "..X...",  
        ".XXXX.",  ".XXXXX",  "XXXXXX",  ".XXXX.",  "...X..",  ".XXXX.",  ".XXXX.",  "..X...",  
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......", 
    
        ".XXXX.",  ".XXXX.",  "......",  "......",  "......",  "......",  "......",  ".XXXX.",  
        "X....X",  "X....X",  "......",  "..X...",  "...X..",  "......",  "..X...",  "X....X",  
        ".XXXX.",  "X....X",  "..X...",  "......",  "..X...",  ".XXXXX",  "...X..",  "....X.",  
        "X....X",  ".XXXXX",  "......",  "......",  ".X....",  "......",  "....X.",  "...X..",  
        "X....X",  ".....X",  "......",  "..X...",  "..X...",  ".XXXXX",  "...X..",  "......",  
        ".XXXX.",  ".XXXX.",  "..X...",  "..X...",  "...X..",  "......",  "..X...",  "...X..",
        "......",  "......",  "......",  ".X....",  "......",  "......",  "......",  "......",
    
        "...XXXX",  "...XXXX",  "XXXXXX.",  "..XXXXX",  "XXXXX..",  "XXXXXXX",  "XXXXXXX",  "..XXXXX",  
        "..X.X.X",  "..X...X",  "X.....X",  ".X.....",  "X....X.",  "X......",  "X......",  ".X.....",  
        ".X.X..X",  ".X....X",  "X....X.",  "X......",  "X.....X",  "X......",  "X......",  "X......",  
        "X..X..X",  "XXXXX.X",  "X.XXX..",  "X......",  "X.....X",  "X.XXXX.",  "X.XXXX.",  "X..XXXX",  
        "X..XXXX",  "X.....X",  "X....X.",  "X......",  "X.....X",  "X......",  "X......",  "X.....X",  
        "X......",  "X.....X",  "X.....X",  ".X.....",  "X....X.",  ".X.....",  "X......",  ".X....X",
        ".XXXXX.",  "X.....X",  "XXXXXX.",  "..XXXXX",  "XXXXX..",  "..XXXXX",  "X......",  "..XXXXX", 
    
        "X.....X",  "...X...",  "......X",  "X.....X",  "X......",  "X.....X",  "X.....X",  "..XXX..",  
        "X.....X",  "...X...",  "......X",  "X....X.",  "X......",  "X.....X",  "XX....X",  ".X...X.",  
        "X.....X",  "...X...",  "......X",  "X...X..",  "X......",  "XX...XX",  "X.X...X",  "X.....X",  
        "XXXXXXX",  "...X...",  "......X",  "X.XX...",  "X......",  "X.X.X.X",  "X..X..X",  "X.....X",  
        "X.....X",  "...X...",  ".....X.",  "X...X..",  ".X.....",  "X..X..X",  "X...X.X",  "X.....X",  
        "X.....X",  "...X...",  "....X..",  "X....X.",  "..X....",  "X.....X",  "X....XX",  ".X...X.",
        "X.....X",  "...X...",  "XXXX...",  "X.....X",  "...XXXX",  "X.....X",  "X.....X",  "..XXX..",
    
        "XXXXX.",  ".XXXX.",  "XXXXX.",  ".XXXX.",  ".XXXXX",  "X....X",  "X....X",  "X....X",  
        "X....X",  "X....X",  "X....X",  "X.....",  "...X..",  "X....X",  "X....X",  "X....X",  
        "X....X",  "X....X",  "X....X",  ".XXXX.",  "...X..",  "X....X",  "X....X",  "X....X",  
        "XXXXX.",  "X.X..X",  "XXXXX.",  ".....X",  "...X..",  "X....X",  "X....X",  "X....X",  
        "X.....",  "X..X.X",  "X...X.",  "X....X",  "...X..",  "X....X",  ".X..X.",  "X.XX.X",  
        "X.....",  ".XXXX.",  "X....X",  ".XXXX.",  "...X..",  ".XXXX.",  "..XX..",  ".X..X.",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......",
    
        "X....X",  ".X...X",  "XXXXXX",  "...XXX",  "......",  "XXX...",  "..X...",  "......",  
        ".X..X.",  ".X...X",  "....X.",  "...X..",  "X.....",  "..X...",  ".X.X..",  "......",  
        "..XX..",  "..X.X.",  "...X..",  "...X..",  ".X....",  "..X...",  "X...X.",  "......",  
        "..XX..",  "...X..",  "..X...",  "...X..",  "..X...",  "..X...",  "......",  "......",  
        ".X..X.",  "...X..",  ".X....",  "...X..",  "...X..",  "..X...",  "......",  "......",  
        "X....X",  "...X..",  "XXXXXX",  "...XXX",  "....X.",  "XXX...",  "......",  "......",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "XXXXXX",
    };
    
    public static void fontConverter(final String[] font) {

        final int matrix_width = 8;
        final int matrix_height = 8;
        final int font_width = 7;
        final int font_height = 7;
        
        for (int matrix_y = 0; matrix_y < matrix_height; matrix_y++) {
            for (int matrix_x = 0; matrix_x < matrix_width; matrix_x++) {
                int start = (matrix_y * matrix_width * font_height) + matrix_x;
                long b = 0;
                for (int row = 0; row < font_height; row++) {
                    b = b << font_width;
                    long v = 1 << (font_width - 1);
                    for (int col = 0; col < font_width; col++) {
                        int i = start + matrix_width * row;
                        if (font[i].charAt(col) == 'X') b += v;
                        else if (font[i].charAt(col) != '.') throw new RuntimeException("wrong char: " + font[i].charAt(col));
                        v = v >> 1;
                    }
                }
                String s = Long.toHexString(b).toUpperCase();
                while (s.length() < 12) s = "0" + s;
                System.out.print("0x" + s + "L,");
            }
            System.out.println();
        }
        
    } 
    
    public static void main(final String[] args) {

        fontConverter(font_light);
        System.out.println();
        
    } 

} 
