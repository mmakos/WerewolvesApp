package werewolves.connect;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;

public class Arrow extends View{
    public static final String PROPERTY_X = "PROPERTY_X";
    public static final String PROPERTY_Y = "PROPERTY_Y";

    private final static double ARROW_ANGLE = Math.PI / 6;
    private final static double ARROW_SIZE = 50;

    private Paint mPaint;

    private boolean mDrawArrow = false;
    private Point mPointFrom = new Point();
    private Point mPointTo = new Point();

    public Arrow( Context context ){
        super( context );
    }

    public Arrow( Context context, AttributeSet attrs ){
        super( context, attrs );
    }

    public Arrow( Context context, AttributeSet attrs, int defStyleAttr ){
        super( context, attrs, defStyleAttr );
    }

    public void draw( int fromX, int fromY, int toX, int toY, int duration ){
        setWillNotDraw( false );
        mPaint = new Paint();
        mPaint.setStyle( Paint.Style.STROKE );
        mPaint.setAntiAlias( true );
        mPaint.setColor( Color.RED );
        mPaint.setAlpha( 150 );
        mPaint.setStrokeWidth( 10 );
        mPointFrom.x = fromX;
        mPointFrom.y = fromY;
        mPointTo.x = toX;
        mPointTo.y = toY;
        animateArrows( duration );
    }

    @Override
    public void dispatchDraw( Canvas canvas ){
        super.dispatchDraw( canvas );
        canvas.save();
        if( mDrawArrow ){
            drawArrowLines( mPointFrom, mPointTo, canvas );
        }
        canvas.restore();
    }


    private void drawArrowLines( Point pointFrom, Point pointTo, Canvas canvas ){
        canvas.drawLine( pointFrom.x, pointFrom.y, pointTo.x, pointTo.y, mPaint );

        double angle = Math.atan2( pointTo.y - pointFrom.y, pointTo.x - pointFrom.x );

        int arrowX, arrowY;

        arrowX = ( int ) ( pointTo.x - ARROW_SIZE * Math.cos( angle + ARROW_ANGLE ) );
        arrowY = ( int ) ( pointTo.y - ARROW_SIZE * Math.sin( angle + ARROW_ANGLE ) );
        canvas.drawLine( pointTo.x, pointTo.y, arrowX, arrowY, mPaint );

        arrowX = ( int ) ( pointTo.x - ARROW_SIZE * Math.cos( angle - ARROW_ANGLE ) );
        arrowY = ( int ) ( pointTo.y - ARROW_SIZE * Math.sin( angle - ARROW_ANGLE ) );
        canvas.drawLine( pointTo.x, pointTo.y, arrowX, arrowY, mPaint );
    }

    private void animateArrows( int duration ){
        mDrawArrow = true;

        ValueAnimator arrowAnimator = createArrowAnimator( mPointFrom, mPointTo, duration );
        arrowAnimator.start();
    }

    private ValueAnimator createArrowAnimator( Point pointFrom, Point pointTo, int duration ){

        final double angle = Math.atan2( pointTo.y - pointFrom.y, pointTo.x - pointFrom.x );

        mPointFrom.x = pointFrom.x;
        mPointFrom.y = pointFrom.y;

        int firstX = ( int ) ( pointFrom.x + ARROW_SIZE * Math.cos( angle ) );
        int firstY = ( int ) ( pointFrom.y + ARROW_SIZE * Math.sin( angle ) );

        PropertyValuesHolder propertyX = PropertyValuesHolder.ofInt( PROPERTY_X, firstX, pointTo.x );
        PropertyValuesHolder propertyY = PropertyValuesHolder.ofInt( PROPERTY_Y, firstY, pointTo.y );

        ValueAnimator animator = new ValueAnimator();
        animator.setValues( propertyX, propertyY );
        animator.setDuration( duration );
        // set other interpolator (if needed) here:
        animator.setInterpolator( new AccelerateDecelerateInterpolator() );

        animator.addUpdateListener( valueAnimator -> {
            mPointTo.x = ( int ) valueAnimator.getAnimatedValue( PROPERTY_X );
            mPointTo.y = ( int ) valueAnimator.getAnimatedValue( PROPERTY_Y );

            invalidate();
        } );

        return animator;
    }
}