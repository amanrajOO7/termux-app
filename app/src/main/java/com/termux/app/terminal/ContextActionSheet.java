package com.termux.app.terminal;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A Material bottom sheet that replaces the old long press context menu of the terminal.
 * It has a row of quick actions on top, followed by a list of rows, some of which can be switches.
 */
public class ContextActionSheet {

    public interface Action {
        void run();
    }

    public interface ToggleAction {
        void run(boolean checked);
    }

    private static class Item {
        final CharSequence label;
        final boolean enabled;
        final Action action;
        final ToggleAction toggleAction;
        final boolean checked;
        @Nullable CharSequence subtitle;
        @DrawableRes int iconRes;
        final boolean isDivider;

        Item(CharSequence label, boolean enabled, Action action, ToggleAction toggleAction, boolean checked, @DrawableRes int iconRes) {
            this.label = label;
            this.enabled = enabled;
            this.action = action;
            this.toggleAction = toggleAction;
            this.checked = checked;
            this.iconRes = iconRes;
            this.isDivider = false;
        }

        static Item divider() {
            return new Item(true);
        }

        private Item(boolean isDivider) {
            this.label = "";
            this.enabled = false;
            this.action = null;
            this.toggleAction = null;
            this.checked = false;
            this.iconRes = 0;
            this.isDivider = isDivider;
        }
    }

    private final Context mContext;
    private final List<Item> mQuickActions = new ArrayList<>();
    private final List<Item> mRows = new ArrayList<>();
    @Nullable private Runnable mOnDismiss;

    public ContextActionSheet(@NonNull Context context) {
        mContext = context;
    }

    /** Add a button to the row of quick actions on top of the sheet. */
    public ContextActionSheet addQuickAction(@StringRes int label, boolean enabled, @NonNull Action action) {
        return addQuickAction(0, label, enabled, action);
    }

    public ContextActionSheet addQuickAction(@DrawableRes int icon, @StringRes int label, boolean enabled, @NonNull Action action) {
        mQuickActions.add(new Item(mContext.getText(label), enabled, action, null, false, icon));
        return this;
    }

    /** Add a row to the sheet. */
    public ContextActionSheet addRow(CharSequence label, boolean enabled, @NonNull Action action) {
        return addRow(0, label, null, enabled, action);
    }

    public ContextActionSheet addRow(@StringRes int label, boolean enabled, @NonNull Action action) {
        return addRow(0, mContext.getText(label), null, enabled, action);
    }

    public ContextActionSheet addRow(@DrawableRes int icon, @StringRes int label, boolean enabled, @NonNull Action action) {
        return addRow(icon, mContext.getText(label), null, enabled, action);
    }

    public ContextActionSheet addRow(@DrawableRes int icon, CharSequence label, boolean enabled, @NonNull Action action) {
        return addRow(icon, label, null, enabled, action);
    }

    /** Add a row with a second line of smaller text under its label. A null subtitle hides the line. */
    public ContextActionSheet addRow(CharSequence label, @Nullable CharSequence subtitle, boolean enabled, @NonNull Action action) {
        return addRow(0, label, subtitle, enabled, action);
    }

    public ContextActionSheet addRow(@DrawableRes int icon, CharSequence label, @Nullable CharSequence subtitle, boolean enabled, @NonNull Action action) {
        Item item = new Item(label, enabled, action, null, false, icon);
        item.subtitle = subtitle;
        mRows.add(item);
        return this;
    }

    /** Add a row with a switch to the sheet. */
    public ContextActionSheet addSwitchRow(@StringRes int label, boolean checked, @NonNull ToggleAction action) {
        return addSwitchRow(0, label, checked, action);
    }

    public ContextActionSheet addSwitchRow(@DrawableRes int icon, @StringRes int label, boolean checked, @NonNull ToggleAction action) {
        mRows.add(new Item(mContext.getText(label), true, null, action, checked, icon));
        return this;
    }

    /** Add a subtle horizontal divider between sections. */
    public ContextActionSheet addDivider() {
        if (!mRows.isEmpty() && !mRows.get(mRows.size() - 1).isDivider) {
            mRows.add(Item.divider());
        }
        return this;
    }

    /** Set a callback that is run after the sheet is dismissed, after an action was run if one was picked. */
    public ContextActionSheet setOnDismiss(@Nullable Runnable onDismiss) {
        mOnDismiss = onDismiss;
        return this;
    }

    public void show() {
        final BottomSheetDialog dialog = new BottomSheetDialog(mContext);
        LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
        View content = inflater.inflate(R.layout.sheet_context_menu, null, false);

        LinearLayout quickActions = content.findViewById(R.id.sheet_quick_actions);
        LinearLayout rows = content.findViewById(R.id.sheet_rows);

        float density = mContext.getResources().getDisplayMetrics().density;
        if (mQuickActions.isEmpty()) {
            quickActions.setVisibility(View.GONE);
        } else {
            int margin = Math.round(4 * density);
            for (Item item : mQuickActions) {
                MaterialButton button = new MaterialButton(dialog.getContext(), null,
                    com.google.android.material.R.attr.materialButtonTonalStyle);
                button.setText(item.label);
                button.setEnabled(item.enabled);
                button.setAllCaps(false);
                if (item.iconRes != 0) {
                    button.setIconResource(item.iconRes);
                    button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
                    button.setIconPadding(Math.round(4 * density));
                    button.setIconSize(Math.round(18 * density));
                }
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                params.setMargins(margin, 0, margin, 0);
                button.setLayoutParams(params);
                button.setOnClickListener(v -> {
                    item.action.run();
                    dialog.dismiss();
                });
                quickActions.addView(button);
            }
        }

        int dividerColor = MaterialColors.getColor(dialog.getContext(), com.google.android.material.R.attr.colorOutlineVariant, 0x1F000000);
        for (Item item : mRows) {
            if (item.isDivider) {
                View divider = new View(dialog.getContext());
                divider.setBackground(new ColorDrawable(dividerColor));
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(1 * density));
                divParams.setMargins(0, Math.round(6 * density), 0, Math.round(6 * density));
                rows.addView(divider, divParams);
                continue;
            }

            View row = inflater.inflate(R.layout.item_context_menu_row, rows, false);
            ImageView icon = row.findViewById(R.id.sheet_row_icon);
            if (item.iconRes != 0 && icon != null) {
                icon.setImageResource(item.iconRes);
                icon.setVisibility(View.VISIBLE);
            }

            TextView label = row.findViewById(R.id.sheet_row_label);
            label.setText(item.label);
            if (item.subtitle != null) {
                TextView subtitle = row.findViewById(R.id.sheet_row_subtitle);
                subtitle.setText(item.subtitle);
                subtitle.setVisibility(View.VISIBLE);
            }
            row.setEnabled(item.enabled);
            row.setAlpha(item.enabled ? 1f : 0.38f);

            MaterialSwitch toggle = row.findViewById(R.id.sheet_row_switch);
            if (item.toggleAction != null) {
                toggle.setVisibility(View.VISIBLE);
                toggle.setChecked(item.checked);
            }

            row.setOnClickListener(v -> {
                if (item.toggleAction != null) {
                    boolean checked = !toggle.isChecked();
                    toggle.setChecked(checked);
                    item.toggleAction.run(checked);
                } else {
                    item.action.run();
                    dialog.dismiss();
                }
            });
            rows.addView(row);
        }

        dialog.setContentView(content);
        dialog.setOnDismissListener(d -> {
            if (mOnDismiss != null) mOnDismiss.run();
        });
        dialog.show();

        // Open fully expanded, the sheet is a list the user wants to scan.
        BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }

}
