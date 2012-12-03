package com.dc.drawing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class DrawingSurfaceView extends View {

	private DrawingActivity parent;
	private Paint paint = new Paint();
	
	private Shape line;
	private SerializablePath path = new SerializablePath();
	
	private int selectedLineIndex = -1;
	
	private int currentWidth = 1;
	private float HALF_STROKE_WIDTH = currentWidth / 2;
	
	private int currentRed = 0;
	private int currentGreen = 0;
	private int currentBlue = 0;

	private PathEffect editingEffect = new DashPathEffect(new float[] { 10, 20 }, 0);

	private CopyOnWriteArrayList<Shape> lines = new CopyOnWriteArrayList<Shape>();
	private boolean editing = false;
	private boolean moving = false;

	
	private int updateCount;
	
	/**
	 * Optimizes painting by invalidating the smallest possible area.
	 */
	private float lastTouchX;
	private float lastTouchY;
	private final RectF dirtyRect = new RectF();

	public DrawingSurfaceView(Context context) {
		super(context);

		paint.setAntiAlias(true);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
	}

	public void setParent(DrawingActivity parent) {
		this.parent = parent;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Iterator<Shape> it = lines.iterator();
		int idx=0;
		while (it.hasNext()) {
			Shape s = it.next();
			
			if (s.deleteOnNextCycle()) {
				lines.remove(s);
				parent.buttonCheck();
				continue;
			}
			
			SerializablePath p = s.getPath();
			paint.setColor(Color.rgb(s.getrgb()[0], s.getrgb()[1],s.getrgb()[2]));
			paint.setStrokeWidth(s.getStrokeWidth());
			
			//are we currently editing this?
			if (idx == selectedLineIndex) {
				paint.setPathEffect(editingEffect);
			} else {
				paint.setPathEffect(null);
			}
			
			
			canvas.drawPath(p, paint);
			idx++;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float eventX = event.getX();
		float eventY = event.getY();
				
		if (editing&&!moving) {
			return true;
		}
		
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			updateCount=0;
			if (moving) {
				line = getCurrentShape();
				path = line.getPath();
				line.setCentre();
				lastTouchX = eventX;
				lastTouchY = eventY;
			} else {
				line = new Shape(currentWidth, currentRed, currentGreen, currentBlue);
				path = new SerializablePath();
				line.setPath(path);
				lines.add(line);
				Log.d("Surface", "Created a new line (Tag: " + line.getTag() + ")");
				path.moveTo(eventX, eventY);
				lastTouchX = eventX;
				lastTouchY = eventY;
				return true;
			}
		case MotionEvent.ACTION_MOVE:
			updateCount++;
			Log.d("Surface","Updated " + updateCount + " times");
			if (moving) {
				if (line!=null) {
					float diffX = eventX-lastTouchX;
					float diffY = eventY-lastTouchY;
					Log.d("DEBUG","Setting offset: " + diffX + "," + diffY);
					line.setOffset(eventX-lastTouchX, eventY-lastTouchY);
					path.offset(eventX-lastTouchX,eventY-lastTouchY);
					path.moveTo(eventX, eventY);
					lastTouchX=eventX;
					lastTouchY=eventY;

					invalidate(
							(int)line.getBounds().left - (int)line.getStrokeWidth(),
							(int)line.getBounds().top - (int)line.getStrokeWidth(),
							(int)line.getBounds().right + (int)line.getStrokeWidth(),
							(int)line.getBounds().bottom + (int)line.getStrokeWidth()
							);
					line.setCentre();
					invalidate(
							(int)line.getBounds().left - (int)line.getStrokeWidth(),
							(int)line.getBounds().top - (int)line.getStrokeWidth(),
							(int)line.getBounds().right + (int)line.getStrokeWidth(),
							(int)line.getBounds().bottom + (int)line.getStrokeWidth()
							);
				}
			} else {
				// Start tracking the dirty region.
				resetDirtyRect(eventX, eventY);

				// When the hardware tracks events faster than they are
				// delivered,
				// the
				// event will contain a history of those skipped points.
				int historySize = event.getHistorySize();
				for (int i = 0; i < historySize; i++) {
					float historicalX = event.getHistoricalX(i);
					float historicalY = event.getHistoricalY(i);
					expandDirtyRect(historicalX, historicalY);
					path.lineTo(historicalX, historicalY);
				}

				// After replaying history, connect the line to the touch point.
				path.lineTo(eventX, eventY);
				if (updateCount>=15) {
					sendDrawnShape(line);
					updateCount = 0;
				}
			}
			break;
		case MotionEvent.ACTION_UP:
			updateCount = 0;
			if (moving) {
				
			} else {
				line.setCentre();
				sendDrawnShape(line);
			}
			break;

		default:
			return false;
		}

		// Include half the stroke width to avoid clipping.
		invalidate((int) (dirtyRect.left - HALF_STROKE_WIDTH),
				(int) (dirtyRect.top - HALF_STROKE_WIDTH),
				(int) (dirtyRect.right + HALF_STROKE_WIDTH),
				(int) (dirtyRect.bottom + HALF_STROKE_WIDTH));

		lastTouchX = eventX;
		lastTouchY = eventY;

		return true;
	}

	/**
	 * Called when replaying history to ensure the dirty region includes all
	 * points.
	 */
	private void expandDirtyRect(float historicalX, float historicalY) {
		if (historicalX < dirtyRect.left) {
			dirtyRect.left = historicalX;
		} else if (historicalX > dirtyRect.right) {
			dirtyRect.right = historicalX;
		}
		if (historicalY < dirtyRect.top) {
			dirtyRect.top = historicalY;
		} else if (historicalY > dirtyRect.bottom) {
			dirtyRect.bottom = historicalY;
		}
	}

	/**
	 * Resets the dirty region when the motion event occurs.
	 */
	private void resetDirtyRect(float eventX, float eventY) {

		// The lastTouchX and lastTouchY were set when the ACTION_DOWN
		// motion event occurred.
		dirtyRect.left = Math.min(lastTouchX, eventX);
		dirtyRect.right = Math.max(lastTouchX, eventX);
		dirtyRect.top = Math.min(lastTouchY, eventY);
		dirtyRect.bottom = Math.max(lastTouchY, eventY);
	}

	public CopyOnWriteArrayList<Shape> getShapes() {
		return lines;
	}

	public void drawReceivedShape(ArrayList<Shape> shapes) {
	
		for (Shape newShape : shapes) {
			
			ListIterator<Shape> it = lines.listIterator();
			boolean replaced = false;
			int idx = 0;
			
			while(it.hasNext()) {
				Shape oldShape = it.next();
				//editing
				if (newShape.getTag() == oldShape.getTag()) {
//					it.set(newShape); fuck you android
					newShape.getPath().offset(newShape.getTotalOffsetX(), newShape.getTotalOffsetY());
					newShape.getPath().moveTo(1, 1);
					lines.set(idx, newShape);
					replaced=true;
				}
				idx++;
			}
			
			//only add the shape if it hasn't been replaced.
			//ie only add if we are getting a new shape rather
			//than getting an edited shape. This keeps the z-order
			//of shapes correct
			if (!replaced) {
				lines.add(newShape);
			}
			
			invalidate();
		}
	}

	public void setLineWidth(int _width) {
		int width = _width+1;
		int dashamount = width;
		
		if (dashamount<10) {
			dashamount=10;
		}
		
		editingEffect = new DashPathEffect(new float[] { dashamount, dashamount+10 }, 0);
		
		if (editing) {
			lines.get(selectedLineIndex).setStrokeWidth(width);
			invalidate();
		} else {
			currentWidth = width;
			HALF_STROKE_WIDTH = currentWidth / 2;
		}
	}

	private void sendDrawnShape(Shape s) {
		parent.sendShapeFromDrawingSurface(s);
	}
	
	public int getColour()
	{					
		if (editing && validate(selectedLineIndex)) {
			int colour[] = lines.get(selectedLineIndex).getrgb();
			return Color.rgb(colour[0],colour[1],colour[2]);
		}
		
		return Color.rgb(currentRed, currentGreen, currentBlue);
	}

	public void setColour(int color) {
		int r = Color.red(color);
		int g = Color.green(color);
		int b = Color.blue(color);
		
		if (editing) {
			lines.get(selectedLineIndex).setrgb(r,g,b);
			invalidate();
		} else {
			currentRed = r;
			currentGreen = g;
			currentBlue = b;
		}
	}

	public void setEditing() {
		Log.d("Surface", "Editing is now " + editing);
		editing = true;
		selectedLineIndex = lines.size() - 1;
		invalidate();
	}
	
	public void commitEdits() {
		sendEdits();
		selectedLineIndex = -1;
		editing = false;
		moving = false;
	}
	
	public void sendEdits() {
		Shape editedShape = getCurrentShape();
		sendDrawnShape(editedShape);
		invalidate();
	}

	public boolean isEditing() {
		return this.editing;
	}
	
	public float getSelectedShapeWidth() {
		if (validate(selectedLineIndex)) {
			return getCurrentShape().getStrokeWidth() - 1;
		}
		
		return 0;
	}
	
	public void selectNext() {
		sendEdits();
		if (selectedLineIndex < lines.size()-1) {
			selectedLineIndex++;
		}
	}
	
	public void selectPrev() {
		sendEdits();
		if (selectedLineIndex > 0) {
			selectedLineIndex--;
		}
		invalidate();
	}
	
	public void deleteCurrentItem() {
		if (validate(selectedLineIndex)) {
			Shape toDelete = getCurrentShape();
			toDelete.setDeleteOnNextCycle(true);
			sendEdits();
		}
		
		if (validate(selectedLineIndex-1)) {
			selectedLineIndex--;
		} else if (validate(selectedLineIndex+1)) {
			selectedLineIndex++;
		} else {
			selectedLineIndex=-1;
			editing=false;
		}
		
		invalidate();
	}
	
	public void setMoving() {
		moving=!moving;
	}
	
	public boolean isMoving() {
		return moving;
	}
	
	private boolean validate(int index) {
		if (index < 0 || index > lines.size()-1) { return false; }
		return true;
	}
	
	public boolean isOnlyItem() {
		return isAtLastItem() && isAtFirstItem();
	}
	
	public boolean hasItems() {
		if (lines.size() > 0) {
			return true;
		}
		
		return false;
	}
	
	public boolean isAtLastItem() {
		if (selectedLineIndex == lines.size()-1) {
			return true;
		}
		
		return false;
	}
	
	public boolean isAtFirstItem() {
		if (selectedLineIndex == 0) {
			return true;
		}
		
		return false;
	}
	
	public Shape getCurrentShape() {
		if (validate(selectedLineIndex)) {
			return lines.get(selectedLineIndex);
		}
		return null;
	}
}