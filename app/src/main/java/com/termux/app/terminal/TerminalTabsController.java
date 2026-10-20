package com.termux.app.terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the open terminal sessions as a row of tabs above the terminal. The selected tab uses the
 * primary color, the other tabs the secondary container color and the add button the tertiary
 * container color. Each tab has
 * a small x to close it, and a + at the end of the row adds a new session.
 *
 * Tapping a tab switches to its session and long pressing it renames the session.
 *
 * The tabs follow the sessions of the {@link TermuxService}, so they are rebuilt from
 * {@link TermuxActivity#updateTerminalTabs()} whenever the sessions, their titles or the current
 * session change.
 */
public class TerminalTabsController {

    private static final int MAX_TITLE_LENGTH = 20;

    private final TermuxActivity mActivity;
    private final View mBar;
    private final HorizontalScrollView mScroll;
    private final LinearLayout mStrip;
    private final float mDensity;

    /** What the tabs were last built with, so they are only rebuilt when something changed. */
    private final List<TerminalSession> mShownSessions = new ArrayList<>();
    private final List<String> mShownTitles = new ArrayList<>();
    private int mShownSelected = -1;

    public TerminalTabsController(@NonNull TermuxActivity activity, @NonNull View bar,
                                  @NonNull HorizontalScrollView scroll, @NonNull LinearLayout strip) {
        mActivity = activity;
        mBar = bar;
        mScroll = scroll;
        mStrip = strip;
        mDensity = activity.getResources().getDisplayMetrics().density;
    }

    /** Make the tabs match the sessions of the service and the current session. */
    public void update() {
        TermuxService service = mActivity.getTermuxService();
        boolean show = service != null && mActivity.getPreferences().shouldShowSessionTabs();
        mBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        List<TerminalSession> sessions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < service.getTermuxSessionsSize(); i++) {
            TermuxSession termuxSession = service.getTermuxSession(i);
            if (termuxSession == null) continue;
            TerminalSession session = termuxSession.getTerminalSession();
            sessions.add(session);
            titles.add(titleOf(session, sessions.size()));
        }
        int selected = sessions.indexOf(mActivity.getCurrentSession());

        if (!sessions.equals(mShownSessions) || !titles.equals(mShownTitles) || selected != mShownSelected) {
            rebuild(sessions, titles, selected);
        }
    }

    private void rebuild(List<TerminalSession> sessions, List<String> titles, int selected) {
        mStrip.removeAllViews();

        View selectedView = null;
        for (int i = 0; i < sessions.size(); i++) {
            View tab = createTab(sessions.get(i), titles.get(i), i == selected);
            if (i == selected) selectedView = tab;
            mStrip.addView(tab);
        }
        mStrip.addView(createAddButton());

        mShownSessions.clear();
        mShownSessions.addAll(sessions);
        mShownTitles.clear();
        mShownTitles.addAll(titles);
        mShownSelected = selected;

        // Keep the selected tab in view when there are more tabs than fit on screen.
        if (selectedView != null) {
            final View toShow = selectedView;
            mScroll.post(() -> mScroll.smoothScrollTo(Math.max(0, toShow.getLeft() - dp(24)), 0));
        }
    }

    private View createTab(TerminalSession session, String title, boolean selected) {
        Context context = mActivity;

        // Selected: solid primary. Inactive: secondary container. Both follow dynamic color.
        int background = selected
            ? color(context, androidx.appcompat.R.attr.colorPrimary, Color.LTGRAY)
            : color(context, com.google.android.material.R.attr.colorSecondaryContainer, Color.GRAY);
        int foreground = selected
            ? color(context, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
            : color(context, com.google.android.material.R.attr.colorOnSecondaryContainer, Color.BLACK);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(18));
        // A ripple in the text color, so the tap is visible on the colored pill.
        int highlight = ColorUtils.setAlphaComponent(foreground, 0x33);

        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(android.view.Gravity.CENTER_VERTICAL);
        // 12dp from the edge to the label, and 12dp from the edge to the visible close stroke.
        // The close button draws a 14dp icon with 5dp padding, and its stroke is about 10.5dp, so
        // it is inset 6.75dp in its 24dp box, which the 5dp end padding makes up to 12dp.
        tab.setPadding(dp(12), 0, dp(5), 0);
        tab.setBackground(new RippleDrawable(ColorStateList.valueOf(highlight), shape, null));

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        tabParams.setMarginEnd(dp(6));
        tab.setLayoutParams(tabParams);

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(foreground);
        label.setMaxLines(1);
        label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        // Remove the extra font padding above and below the text, so the glyphs are centred
        // in the tab and line up with the close icon.
        label.setIncludeFontPadding(false);
        tab.addView(label);

        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.ic_tab_close);
        close.setBackground(null);
        close.setContentDescription(context.getString(R.string.action_close_tab));
        // 24dp touch target with a 14dp icon: the icon stroke is about the cap height of the label.
        close.setPadding(dp(5), dp(5), dp(5), dp(5));
        ImageViewCompat.setImageTintList(close, ColorStateList.valueOf(foreground));
        close.setOnClickListener(v -> closeTab(session));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        // About 8dp between the end of the label and the visible close stroke.
        closeParams.setMarginStart(dp(1));
        tab.addView(close, closeParams);

        tab.setOnClickListener(v -> {
            if (session != mActivity.getCurrentSession()) {
                mActivity.getTermuxTerminalSessionClient().setCurrentSession(session);
            }
        });
        tab.setOnLongClickListener(v -> {
            mActivity.getTermuxTerminalSessionClient().renameSession(session);
            return true;
        });

        return tab;
    }

    private View createAddButton() {
        Context context = mActivity;
        ImageButton add = new ImageButton(context);
        add.setImageResource(R.drawable.ic_tab_add);
        add.setContentDescription(context.getString(R.string.action_new_session));
        // A round neutral pill, the same height as the tabs, so it reads as a button but not as a tab.
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.OVAL);
        int addBackground = color(context, com.google.android.material.R.attr.colorTertiaryContainer, Color.GRAY);
        int addForeground = color(context, com.google.android.material.R.attr.colorOnTertiaryContainer, Color.BLACK);
        pill.setColor(addBackground);
        add.setBackground(new RippleDrawable(ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(addForeground, 0x33)),
            pill, null));
        // Same visible size as the close icon: a 14dp icon in the 36dp button.
        add.setPadding(dp(11), dp(11), dp(11), dp(11));
        ImageViewCompat.setImageTintList(add, ColorStateList.valueOf(addForeground));
        add.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().addNewSession(false, null));
        add.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return add;
    }

    private void closeTab(TerminalSession session) {
        if (session.isRunning()) {
            new AlertDialog.Builder(mActivity)
                .setMessage(R.string.title_confirm_kill_process)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> session.finishIfRunning())
                .setNegativeButton(android.R.string.no, null)
                .show();
        } else {
            mActivity.getTermuxTerminalSessionClient().removeFinishedSession(session);
        }
    }

    /** The name of the session, else its title from the shell, else a numbered default. */
    @NonNull
    private String titleOf(TerminalSession session, int number) {
        String title = null;
        if (session.mSessionName != null && !session.mSessionName.isEmpty()) {
            title = session.mSessionName;
        } else if (session.getTitle() != null && !session.getTitle().isEmpty()) {
            title = session.getTitle();
        }
        if (title == null) {
            title = mActivity.getString(R.string.tab_default_title, number);
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
        }
        return title;
    }

    private int color(Context context, int attr, int fallback) {
        return MaterialColors.getColor(context, attr, fallback);
    }

    private int dp(int value) {
        return Math.round(value * mDensity);
    }

}
