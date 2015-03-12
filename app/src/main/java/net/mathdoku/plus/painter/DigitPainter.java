package net.mathdoku.plus.painter;

import android.graphics.Paint;

import net.mathdoku.plus.painter.Painter.DigitPainterMode;

abstract class DigitPainter extends BasePainter {

    // Painters for the different input modes
    Paint mTextPaintNormalInputMode;
    Paint mTextPaintMaybeInputMode;

    DigitPainterMode mDigitPainterMode;

    // Offsets (bottom, left) of the region in which the value will be painted.
    float mLeftOffset;
    float mBottomOffset;

    /**
     * Creates a new instance of {@link DigitPainter}.
     *
     * @param painter
     *         The global container for all painters.
     */
    DigitPainter(Painter painter) {
        super(painter);
        setColorMode(DigitPainterMode.INPUT_MODE_BASED);
    }

    /**
     * Gets the paint for the text in normal input mode.
     *
     * @return The paint for the text in normal input mode.
     */
    public Paint getTextPaintNormalInputMode() {
        return mTextPaintNormalInputMode;
    }

    /**
     * Gets the paint for the text in maybe input mode.
     *
     * @return The paint for the text in maybe input mode.
     */
    public Paint getTextPaintMaybeInputMode() {
        return mTextPaintMaybeInputMode;
    }

    /**
     * Gets the horizontal (left) offset for the text inside the cell.
     *
     * @return The horizontal (left) offset for the text inside the cell.
     */
    public float getLeftOffset() {
        return mLeftOffset;
    }

    /**
     * Gets the vertical (top) offset for the text inside the cell.
     *
     * @return The vertical (top) offset for the text inside the cell.
     */
    public float getBottomOffset() {
        return mBottomOffset;
    }

    /**
     * Set the color mode of the digit painter.
     *
     * @param digitPainterMode
     *         The digit painter mode (colored digits versus monochrome) to be used.
     */
    public void setColorMode(DigitPainterMode digitPainterMode) {
        mDigitPainterMode = digitPainterMode;
    }
}