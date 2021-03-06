
/**
 * Plughole: a rolling-ball accelerometer game.
 * <br>Copyright 2008-2010 Ian Cameron Smith
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


package org.hermit.plughole;

import java.util.ArrayList;

import org.hermit.plughole.LevelReader.LevelException;
import org.xmlpull.v1.XmlPullParser;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;


/**
 * Class representing a polygon.  This is designed to represent objects in
 * the level that the ball interacts with.
 * 
 * This is a rather complex class.  Every polygon -- whether it starts as
 * a rectangle or a random poly -- has the potential to be drawn, and also
 * to interact with the ball.  Interactions can take the form of the ball
 * bouncing off it, or crossing it and triggering an action, or triggering
 * an action by being contained within it (certain types only).
 * 
 * The wrinkle is that all ball interactions are done with reference to the
 * centre of the ball; even though it appears to be the edge of the ball
 * which actually bounces off a wall, etc.
 * 
 * So, each Poly is actually 2 polygons.  The level data defines the visible
 * polygon, which we turn into a graphics path for drawing on the screen.
 * Then we create a new set of lines, the "effective polygon", by enlarging
 * the visible polygon by the ball radius -- these lines are the ones which
 * take part in ball interactions.  The enlarged line set has its corners
 * rounded.
 */
class Poly
	extends Visual
{

	// ******************************************************************** //
	// Public Types.
	// ******************************************************************** //

    /**
     * Class describing the blocking characteristics, if any, of a Poly.
     */
    static final class Wall {
        Wall(boolean init) {
            this.initial = init;
        }
        
        // True if the poly is initially blocking, false if not.
        public final boolean initial;
    }
    

    /**
     * Class describing the drawing characteristics, if any, of a Poly.
     */
    static final class Draw {
        Draw(int col) {
            this.colour = col;
        }
        
        // Base colour to draw with.
        public final int colour;
    }
    

    // ******************************************************************** //
    // Constructor.
    // ******************************************************************** //
   
    /**
     * Create a polygon with no initial points.  Points will be added
     * later as we read them.
     * 
     * @param   app         Application context.
	 * @param	id			The ID of this element.
	 * @param	xform		Transform to apply to the raw data.
     */
    public Poly(Plughole app, String id, Matrix xform)
    {
        super(app, id, null, xform);
        
    	buildingPoints = new ArrayList<Point>();
    }
    

    /**
     * Create a polygon from the given rectangle.
     * 
     * @param   app         Application context.
	 * @param	id			The ID of this element.
     * @param   visRect     The visible rectangle defining this element, in
     *                      level co-ordinates.
	 * @param	xform		Transform to apply to the raw data.
     */
    public Poly(Plughole app, String id, RectF visRect, Matrix xform)
    {
        super(app, id, visRect, xform);

        // Construct a points list in clockwise order.
        RectF screenRect = xform.transform(visRect);
    	buildingPoints = new ArrayList<Point>(4);
    	buildingPoints.add(new Point(screenRect.left, screenRect.top));
    	buildingPoints.add(new Point(screenRect.right, screenRect.top));
    	buildingPoints.add(new Point(screenRect.right, screenRect.bottom));
    	buildingPoints.add(new Point(screenRect.left, screenRect.bottom));
    }


	/**
	 * Create a new Poly representing a circle as a set of line segments.
	 * 
	 * @param	app			Application context.
	 * @param	id			The ID of this element.
	 * @param	centre		Centre point of the circle, in level co-ordinates.
	 * @param	r			Radius of the circle, in level co-ordinates.
     * @param   visRect     The visible rectangle defining this element, in
     *                      level co-ordinates.
	 * @param	xform		Transform to apply to the raw data.
	 */
	public Poly(Plughole app, String id, Point centre, double r,
	            RectF visRect, Matrix xform)
	{
        super(app, id, visRect, xform);

    	buildingPoints = new ArrayList<Point>(360 / CORNER_SEG + 1);

		// Compute a vector representing three o'clock.
		Vector v1 = new Vector(r, 0);

		// Create points around the circle.
		for (int i = 0; i < 360 / CORNER_SEG; ++i) {
			buildingPoints.add(xform.transform(centre.offset(v1)));
			v1 = v1.rotate(CORNER_SEG);
		}
	}


	// ******************************************************************** //
	// Construction.
	// ******************************************************************** //
	
	/**
	 * Add a child to this element.  This is used during level parsing.
	 * 
	 * @param	p			The parser the level is being read from.
	 * @param	tag 		The name of this item's XML tag.
	 * @param	child		The child to add to this element.
	 * @return				true iff this child has been accepted.  If
	 * 						false, the child is actually a sibling; it
	 * 						has not been added here, and needs to be
	 * 						added to the parent.
     * @throws LevelException 
	 */
	@Override
    boolean addChild(XmlPullParser p, String tag, Object child)
		throws LevelException
	{
		if (child instanceof Point) {
			buildingPoints.add((Point) child);
			return true;
        } else if (child instanceof Wall) {
            isWall = true;
            return true;
        } else if (child instanceof Draw) {
            isDrawn = true;
            drawColour = ((Draw) child).colour;
            return true;
		} else if (child instanceof Action) {
			addAction((Action) child);
			return true;
		} else if (child instanceof Graphic ||
		                    child instanceof Anim || child instanceof Text) {
		    // Child needs its rect.
		    RectF rect = getVisualRect();
		    if (rect == null)
		        throw new LevelException(p, "element <" + tag +
                        "> did not define a rect for <" + p.getName() + ">");
		    ((Visual) child).setRect(rect);
			return false;
		}
		throw new LevelException(p, "element <" + p.getName() +
									"> not permitted in <" + tag + ">");
	}

	
	/**
	 * We're finished adding children; do any required initialization.
     * 
     * @param   p           The parser the level is being read from.
     * @throws LevelException 
	 */
	@Override
    void finished(XmlPullParser p)
        throws LevelException
	{
	    super.finished(p);
	    
	    if (buildingPoints.size() < 3)
            throw new LevelException(p, "a polygon must contain" +
                                        " at least 3 <Point> tags");
	    
	    // Now, we need to create two polygons.  The shape defined by the
	    // user is the one we draw, so convert that into a graphics path.
	    // But since all bounce calculations are done off the ball's centre,
	    // we create a larger polygon to be the actual effective zone.  This
	    // is the one that has all the actions connected to it.
	    
		// We need the ball radius in screen co-ordinates.
        Matrix xform = getTransform();
        double ballRad = LevelData.BALL * xform.getScale() / 2.0;

		// First make the graphics path and a list of lines for the visible
	    // polygon.  Also calculate the visible bounding box.
		drawingPath = new Path();
        final int npoints = buildingPoints.size();
        drawingLines = new Line[npoints];
        screenBounds = new Rect(Integer.MAX_VALUE, Integer.MAX_VALUE,
                                Integer.MIN_VALUE, Integer.MIN_VALUE);
		boolean first = true;
        Point prev = buildingPoints.get(npoints - 1);
        for (int i = 0; i < npoints; ++i) {
		    Point curr = buildingPoints.get(i);
		    
		    // Adjust the bounding box.
		    if (curr.x < screenBounds.left)
		        screenBounds.left = (int) curr.x;
            if (curr.x > screenBounds.right)
                screenBounds.right = (int) curr.x;
            if (curr.y < screenBounds.top)
                screenBounds.top = (int) curr.y;
            if (curr.y > screenBounds.bottom)
                screenBounds.bottom = (int) curr.y;
		    
		    // Add to the path.
		    if (first) {
		        drawingPath.moveTo((float) curr.x, (float) curr.y);
		        first = false;
		    } else
		        drawingPath.lineTo((float) curr.x, (float) curr.y);
		    
		    // Add another line.
		    drawingLines[i] = new Line(prev, curr);
            prev = curr;
		}
		drawingPath.close();
		
		// Now, expand the line set we just created to get the effective lines.
		effectiveLines = createLarger(drawingLines, ballRad);
        
        // Set the appropriate actions on all the effective lines.
        Action[] cross = getActions(Action.Trigger.ONCROSS);
        Action[] bounce = getActions(Action.Trigger.ONBOUNCE);
        for (Line l : effectiveLines) {
            l.setCrossActions(cross);
            l.setBounceActions(bounce);
        }
	}
	

    // ******************************************************************** //
    // Transformations.
    // ******************************************************************** //

	/**
	 * Create a new polygon which is a larger version of a given one.
	 * 
	 * Each line which makes up the poly is moved outwards -- i.e. to the
	 * left -- by a specified distance, in a direction perpendicular
	 * to itself.  A new polygon is constructed from these lines.  Convex
	 * corners are rounded off using multiple line segments.
	 * 
	 * @param	dist			Distance to grow by.
	 * @return					The new, grown polygon, as a line list.
	 */
	private static final Line[] createLarger(Line[] base, double dist) {
		// Create the outward-displaced versions of all our lines.
		Line[] grown = new Line[base.length];
		for (int i = 0; i < base.length; ++i)
			grown[i] = base[i].moveLeft(dist);

		// Create the corners between all pairs of the new, moved lines.
		// These corners become the boundary of the new polygon.
		ArrayList<Point> newpoints = new ArrayList<Point>(grown.length * 4);
		for (int i = 0; i < grown.length; ++i) {
			int j = i + 1;
			if (j >= grown.length)
				j = 0;
			makeCorner(base[i].getEnd(), grown[i], grown[j], newpoints);
		}
		
        final int npoints = newpoints.size();
        Line[] newlines = new Line[npoints];
        Point prev = newpoints.get(npoints - 1);
        for (int i = 0; i < npoints; ++i) {
            Point curr = newpoints.get(i);
            newlines[i] = new Line(prev, curr);
            prev = curr;
        }

        return newlines;
	}


	/**
	 * Make a "rounded" corner between the two given line segments.  If
	 * the corner is concave, these segments intersect; if it's convex,
	 * then we need to make a rounding.  Do so by adding new points to 
	 * the given points list.
	 * 
	 * @param	centre			The centre point of the corner.
	 * @param	l1				First line segment.
	 * @param	l2				Second line segment.
	 * @param	points			Points list -- we will add new points to
	 * 							this as required to make the corner.
	 */
	private static final void makeCorner(Point centre, Line l1, Line l2,
						                 ArrayList<Point> points)
	{
		// Compute the vectors from the centre point to the two line ends.
		Vector v1 = new Vector(centre, l1.getEnd());
		Vector v2 = new Vector(centre, l2.getStart());
		
		// Calculate the angle between the two vectors.
		// Bear in mind the angles are zero towards positive X, positive
		// towards positive Y -- WHICH IS DOWN THE SCREEN -- and negative
		// towards negative Y.
		double turn = v2.angle(v1);
		
		// If the angle is zero, I'm not turning and don't need another
		// point.
		if (turn == 0)
			return;
		
		// If the angle is less than 0, then I'm turning left -- i.e.
		// this is a concave corner.  The corner point is just the
		// intersection of the two lines.
		if (turn < 0) {
			Point np = Line.intersect(l1, l2);
			points.add(np);
			return;
		}
		
		// Otherwise, I'm turning right.   We need to fill in the corner.
		// Use a line segment.
		for (;;) {
			points.add(centre.offset(v1));
			v1 = v1.rotate(CORNER_SEG);
			if (v2.angle(v1) < 0)
				break;
		}
	}
	

    // ******************************************************************** //
    // Accessors.
    // ******************************************************************** //

	/**
	 * Get the overall bounding box of this polygon, in screen
	 * co-ordinates.  This is not the same as the visual rect; it's the
	 * minimal screen rectangle containing all the polygon's visible points.
	 * 
	 * @return             Screen visual bounding box for this polygon.
	 */
	Rect getScreenBounds() {
	    return screenBounds;
	}
	
	
	/**
	 * Determine whether this polygon is a wall.
	 * 
	 * @return             True if this polygon acts as a barrier.
	 */
	boolean isWall() {
	    return isWall;
	}
	

    /**
     * Determine whether this polygon draws its outline.
     * 
     * @return             True if this polygon draws its outline.
     */
    boolean isDrawn() {
        return isDrawn;
    }
    

	/**
	 * Get the lines which make up the effective polygon outline.
	 * 
	 * @return             The lines which make up the effective polygon.
	 *                     These are the lines which the centre of the ball
	 *                     should interact with.
	 */
	Line[] getEffectiveLines() {
		return effectiveLines;
	}


	// ******************************************************************** //
	// State Control.
	// ******************************************************************** //

	/**
	 * Enable or disable this polygon as a barrier which reflects the ball.
	 * 
	 * @param  enable      True iff the ball should bounce off.
	 */
	@Override
    void setEnable(boolean enable) {
	    super.setEnable(enable);
	    
	    // Set the bounce enable state of our lines.
        for (Line l : effectiveLines)
            l.reflectEnabled = enable;
	}
	

    // ******************************************************************** //
    // Drawing.
    // ******************************************************************** //

	/**
	 * Draw this graphic onto the given canvas.
	 * 
	 * @param	canvas			Canvas to draw on.
	 * @param	time			Total level time in ms.  A time of zero
	 * 							indicates that we're drawing statically,
	 * 							not in the game loop.
	 * @param	clock			Level time remaining in ms.
	 */
	@Override
	protected void draw(Canvas canvas, long time, long clock) {
	    if (!isDrawn || !getEnable())
	        return;
	    
        Log.v(TAG, "Draw poly " + getId());
        
		// Clip to the polygon, so when we draw the bevel using thick lines,
		// we don't spread out.
		canvas.save();
		canvas.clipPath(drawingPath);
		
		// Get our base colour as HSV.
		float[] hsvBase = new float[3];
		Color.colorToHSV(drawColour, hsvBase);
		float[] hsvAdj = new float[3];

		// Create a pretty gradient to highlight this polygon with.
		int[] colors = new int[7];
		colors[0] = drawColour;
        colors[1] = bias(hsvBase, 0.1f, hsvAdj);
        colors[2] = bias(hsvBase, 0.2f, hsvAdj);
        colors[3] = bias(hsvBase, 1.0f, hsvAdj);
        colors[4] = bias(hsvBase, 0.2f, hsvAdj);
        colors[5] = bias(hsvBase, 0.1f, hsvAdj);
        colors[6] = drawColour;
        Drawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        shape.setBounds(getScreenBounds());
        
		// Fill the poly.
		shape.draw(canvas);
	
		// Draw a bevelled edge on the outside of the poly.
		polyPaint.setStyle(Paint.Style.STROKE);
		polyPaint.setStrokeWidth(3);
		for (Line l : drawingLines) {
		    // Calculate the bevel highlight / lowlight for this edge based
		    // on its angle.  Zero angle is to +X; positive angles go
		    // anti-clockwise.  But Y is DOWN THE SCREEN.
		    float a = (float) Math.toDegrees(Math.atan2(-l.dy, l.dx));
		    
		    // Normalize so zero is orthogonal to top-left.
		    a -= 45.0;
		    if (a < -180.0)
		        a += 360.0;
		    
		    // Calculate bias from 1 to -1.
		    final float bias = -(Math.abs(a) - 90.0f) / 90.0f;
		    
		    Log.v(TAG, "a=" + Math.toDegrees(Math.atan2(-l.dy, l.dx)) +
		               ", adj=" + a + ", b=" + bias);
		    
		    polyPaint.setColor(bias(hsvBase, bias, hsvAdj));
		    canvas.drawLine((float) l.sx, (float) l.sy,
		                    (float) l.ex, (float) l.ey, polyPaint);
		}
		
		canvas.restore();
	}
	
	
	private static final int bias(float[] base, float bias, float[] adj) {
        adj[0] = base[0];
        adj[1] = base[1];
        adj[2] = base[2] + bias * 0.5f;
        if (adj[2] > 1) {
            adj[1] -= adj[2] - 1;
            if (adj[1] < 0)
                adj[1] = 0;
            adj[2] = 1;
        }
        return Color.HSVToColor(adj);
	}
	
	
	// ******************************************************************** //
	// Class Data.
	// ******************************************************************** //

    // Debugging tag.
	@SuppressWarnings("unused")
	private static final String TAG = "plughole";
	
	// The granularity of rounded "corners" we create when growing
	// a polygon.  A new point is added every CORNER_SEG degrees.
	private static final int CORNER_SEG = 30;

	// Paint we use for drawing all polygons.
	private static final Paint polyPaint = new Paint();
	static {
		polyPaint.setAntiAlias(true);
	}
	
	
	// ******************************************************************** //
	// Private Data.
	// ******************************************************************** //
	
    // True if this polygon acts as a barrier.
	private boolean isWall = false;
	
    // True if this polygon is to draw its outline.
    private boolean isDrawn = false;

    // If drawn, the colour to draw in.
    private int drawColour = LevelData.WALL_COLOR;
    
	// Temporary list used to hold points as we read them from the level
	// data, in screen co-ordinates.
	private ArrayList<Point> buildingPoints = null;

    // The lines defining the visual polygon boundary.  This is used for
    // drawing it.  Null if not set up yet.
    private Line[] drawingLines;

    // A graphics Path representing this polygon.  This is used for
    // drawing it.  Null if not set up yet.
    private Path drawingPath = null;

	// The lines defining the effective polygon boundary.  These are the
	// lines which will interact with the centre of the ball.  These are
	// assumed to go clockwise around the border, so moving from points[0] to
	// points[1], the left side is outside the polygon, right side inside.
	// Of course the last point joins to the first.
	private Line[] effectiveLines;
	
	// The overall bounding box of this polygon, in screen
	// co-ordinates.  This is not the same as the visual rect; it's the
	// minimal screen rectangle containing all the polygon's visible points.
	private Rect screenBounds;
	
}

