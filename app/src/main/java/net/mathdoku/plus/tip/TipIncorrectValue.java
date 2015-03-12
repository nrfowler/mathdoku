package net.mathdoku.plus.tip;

import android.content.Context;

import net.mathdoku.plus.Preferences;
import net.mathdoku.plus.R;

public class TipIncorrectValue extends TipDialog {

    private static final String TIP_NAME = "IncorrectValue";
    private static final TipPriority TIP_PRIORITY = TipPriority.HIGH;

    /**
     * Creates a new tip dialog which explains that an incorrect value has been entered.
     *
     * @param context
     *         The activity in which this tip has to be shown.
     */
    public TipIncorrectValue(Context context) {
        super(context, TIP_NAME, TIP_PRIORITY);

        build(R.drawable.alert, context.getResources()
                      .getString(R.string.dialog_tip_incorrect_value_title), context.getResources()
                      .getString(R.string.dialog_tip_incorrect_value_text), null);
    }

    /**
     * Checks whether this tip has to be displayed. Should be called statically before creating this
     * object.
     *
     * @param preferences
     *         Preferences of the activity for which has to be checked whether this tip should be
     *         shown.
     * @return True in case the tip might be displayed. False otherwise.
     */
    public static boolean toBeDisplayed(Preferences preferences) {
        // Note: No time restriction has been set on this tip. Display each time
        // if applicable.

        // Determine on basis of preferences whether the tip should be shown.
        return TipDialog.getDisplayTipAgain(preferences, TIP_NAME, TIP_PRIORITY);
    }

    /**
     * Ensure that this tip will not be displayed (again).
     */
    public static void doNotDisplayAgain(Preferences preferences) {
        // Determine on basis of preferences whether the tip should be shown.
        preferences.setTipDoNotDisplayAgain(TIP_NAME);
    }
}