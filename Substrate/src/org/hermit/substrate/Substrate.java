
/**
 * Substrate: a collection of eye candies for Android.  Various screen
 * hacks from the xscreensaver collection can be viewed standalone, or
 * set as live wallpapers.
 * <br>Copyright 2010 Ian Cameron Smith
 *
 * <p>This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation (see COPYING).
 * 
 * <p>This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */


package org.hermit.substrate;


import java.util.Random;

import net.goui.util.MTRandom;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;


/**
 * Substrate: grow crystal-like lines on a computational substrate.  This
 * is a port of the code by J. Tarbell at http://complexification.net/.
 *
 * <p>Copyright © 2003 by J. Tarbell (complex@complexification.net).
 * 
 *     "Modifications and extensions of these algorithms are encouraged.
 *      Please send me your experiences."
 */
public class Substrate
    extends EyeCandy
{

    // ******************************************************************** //
    // Constructor.
    // ******************************************************************** //
    
    /**
     * Create a substrate drawing instance.
     */
    public Substrate() {
    }


    // ******************************************************************** //
    // Configuration.
    // ******************************************************************** //

    /**
     * This method is called to notify us that the screen configuration
     * has changed.
     * 
     * @param   width       The width of the substrate.
     * @param   height      The height of the substrate.
     * @param   config      Pixel configuration of the screen.
     */
    @Override
    public void onConfigurationSet(int width, int height, Bitmap.Config config) {
        colourPalette = new PollockPalette();
    }


    // ******************************************************************** //
    // Preferences.
    // ******************************************************************** //

    /**
     * Called when a shared preference is changed, added, or removed.
     * This may be called even if a preference is set to its existing value.
     *
     * @param   prefs       The SharedPreferences that received the change.
     * @param   key         The key of the preference that was changed. 
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        int maxCycles = 10000;
        try {
            String sval = prefs.getString("maxCycles", null);
            maxCycles = Integer.valueOf(sval);
        } catch (Exception e) {
            Log.e(TAG, "Pref: bad maxCycles");
        }
        setMaxCycles(maxCycles);
        Log.i(TAG, "Prefs: maxCycles " + maxCycles);

        try {
            String sval = prefs.getString("maxCracks", null);
            maxCracks = Integer.valueOf(sval);
        } catch (Exception e) {
            Log.e(TAG, "Pref: bad maxCracks");
        }
        Log.i(TAG, "Prefs: maxCracks " + maxCracks);

        try {
            String sval = prefs.getString("sandGrains", null);
            sandGrains = Integer.valueOf(sval);
        } catch (Exception e) {
            Log.e(TAG, "Pref: bad sandGrains");
        }
        Log.i(TAG, "Prefs: sandGrains " + sandGrains);

        reset();
    }

    
    // ******************************************************************** //
    // Control Methods.
    // ******************************************************************** //

    /**
     * Reset this eye candy back to a blank state.  This will be called
     * at start-up, and to reset back to an initial state when the cycle
     * limit is exceeded.
     */
    @Override
    public void reset() {
        if (canvasWidth <= 0 || canvasHeight <= 0)
            return;
        
        crackGrid = new int[canvasWidth * canvasHeight];
        cracks = new Crack[maxCracks];

        // erase crack grid
        for (int y = 0; y < canvasHeight; ++y)
            for (int x = 0; x < canvasWidth; ++x)
                crackGrid[y * canvasWidth + x] = 10001;

        // make random crack seeds
        for (int k = 0; k < 16; k++) {
            int i = MT_RANDOM.nextInt(canvasWidth * canvasHeight - 1);
            crackGrid[i] = MT_RANDOM.nextInt(360);
        }

        // make just three cracks
        numCracks = 0;
        for (int k = 0; k < 3; k++)
            makeCrack();

        // Clear to white.
        renderCanvas.drawColor(0xffffffff);
    }


    // ******************************************************************** //
    // Drawing.
    // ******************************************************************** //

    /**
     * Update this substrate into renderBitmap.
     */
    @Override
    protected void doDraw() {
        // crack all cracks
        for (int n = 0; n < numCracks; ++n)
            cracks[n].move();
    }


    // ******************************************************************** //
    // Private Methods.
    // ******************************************************************** //

    private void makeCrack() {
        if (numCracks < maxCracks) {
            // make a new crack instance
            cracks[numCracks] = new Crack();
            numCracks++;
        }
    }


    // ******************************************************************** //
    // Crack Class.
    // ******************************************************************** //
    
    private class Crack {
        float x, y;
        float t;    // direction of travel in degrees

        // sand painter
        SandPainter sp;

        Crack() {
            // find placement along existing crack
            findStart();
            sp = new SandPainter();
        }

        void findStart() {
            // pick random point
            int px = 0;
            int py = 0;

            // shift until crack is found
            boolean found = false;
            int timeout = 0;
            while (!found || timeout++ > 1000) {
                px = MT_RANDOM.nextInt(canvasWidth);
                py = MT_RANDOM.nextInt(canvasHeight);
                if (crackGrid[py * canvasWidth + px] < 10000)
                    found = true;
            }

            if (found) {
                // start crack
                int a = crackGrid[py*canvasWidth+px];
                if (MT_RANDOM.nextBoolean())
                    a -= 90 + irandom(-2f, 2.1f);
                else
                    a += 90 + irandom(-2f, 2.1f);

                startCrack(px, py, a);
            } else {
                //println("timeout: "+timeout);
            }
        }

        void startCrack(int X, int Y, int T) {
            x=X;
            y=Y;
            t=T;//%360;
            double tr = Math.toRadians(t);
            x += 0.61 * Math.cos(tr);
            y += 0.61 * Math.sin(tr);  
        }

        void move() {
            // continue cracking
            double tr = Math.toRadians(t);
            x += 0.42 * Math.cos(tr);
            y += 0.42 * Math.sin(tr); 

            // bound check
            float z = 0.33f;
            int cx = (int) (x + random(-z, z));  // add fuzz
            int cy = (int) (y + random(-z, z));

            // draw sand painter
            regionColor();

            // draw black crack
            renderPaint.setColor(0xff000000);
            renderPaint.setAlpha(85);
            renderCanvas.drawPoint(x + random(-z, z), y + random(-z, z), renderPaint);
            // stroke(0, 85);
            // point(x + random(-z, z), y + random(-z, z));

            if ((cx>=0) && (cx<canvasWidth) && (cy>=0) && (cy<canvasHeight)) {
                // safe to check
                if ((crackGrid[cy*canvasWidth+cx]>10000) || (Math.abs(crackGrid[cy*canvasWidth+cx]-t)<5)) {
                    // continue cracking
                    crackGrid[cy * canvasWidth + cx] = (int) t;
                } else if (Math.abs(crackGrid[cy*canvasWidth+cx]-t)>2) {
                    // crack encountered (not self), stop cracking
                    findStart();
                    makeCrack();
                }
            } else {
                // out of bounds, stop cracking
                findStart();
                makeCrack();
            }
        }

        void regionColor() {
            // start checking one step away
            float rx=x;
            float ry=y;
            boolean openspace=true;

            // find extents of open space
            while (openspace) {
                // move perpendicular to crack
                double tr = Math.toRadians(t);
                rx += 0.81 * Math.sin(tr);
                ry -= 0.81 * Math.cos(tr);
                int cx = (int) rx;
                int cy = (int) ry;
                if ((cx>=0) && (cx<canvasWidth) && (cy>=0) && (cy<canvasHeight)) {
                    // safe to check
                    if (crackGrid[cy*canvasWidth+cx]>10000) {
                        // space is open
                    } else {
                        openspace=false;
                    }
                } else {
                    openspace=false;
                }
            }
            // draw sand painter
            sp.render(rx,ry,x,y);
        }
    }


    // ******************************************************************** //
    // SandPainter Class.
    // ******************************************************************** //

    private class SandPainter {

        SandPainter() {
            c = colourPalette.getRandom();
            g = random(0.01f, 0.1f);
        }
        
        void render(float x, float y, float ox, float oy) {
            // modulate gain
            g += random(-0.050f, 0.050f);
            float maxg = 1.0f;
            if (g < 0)
                g = 0;
            if (g > maxg)
                g = maxg;

            // lay down grains of sand (transparent pixels)
            float w = g / (sandGrains - 1);
            for (int i = 0; i < sandGrains; i++) {
                final float ssiw = (float) Math.sin(Math.sin(i * w));
                final float px = ox + (x - ox) * ssiw;
                final float py = oy + (y - oy) * ssiw;
                final float a = 0.1f - i / (sandGrains * 10.0f);
                
                renderPaint.setColor(c);
                renderPaint.setAlpha(Math.round(a * 256));
                renderCanvas.drawPoint(px, py, renderPaint);
                // stroke(red(c), green(c), blue(c), a*256);
                // point(px, py);
            }
        }

        // Colour for this SandPainter.
        private int c;
        
        // Gain; used to modulate the alpha for a "fuzzy" effect.
        private float g;
    }

    
    // ******************************************************************** //
    // Utility Methods.
    // ******************************************************************** //
    
    private float random(float a, float b) {
        return MT_RANDOM.nextFloat() * (b - a) + a;
    }

    private int irandom(float a, float b) {
        return (int) random(a, b);
    }

    
    // ******************************************************************** //
    // Class Data.
    // ******************************************************************** //

    // Debugging tag.
    @SuppressWarnings("unused")
    private static final String TAG = "Substrate";
    
    // Random number generator.  We use a Mersenne Twister,
    // which is a high-quality and fast implementation of java.util.Random.
    private static final Random MT_RANDOM = new MTRandom();

    
    // ******************************************************************** //
    // Private Data.
    // ******************************************************************** //

    // Colour palette we're using.
    private Palette colourPalette = null;

    // The number of currently-active cracks.
    private int numCracks = 0;

    // Grid of cracks.
    private int[] crackGrid = null;
    private Crack[] cracks = null;
    
    // The maximum number of cracks we can have on the go at once.
    private int maxCracks = 50;

    // Number of grains of sand to paint.
    private int sandGrains = 50;

}
