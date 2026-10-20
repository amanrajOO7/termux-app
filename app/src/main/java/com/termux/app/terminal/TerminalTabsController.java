package com.termux.app.terminal;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.tabs.TabLayout;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the open terminal sessions as tabs above the terminal. Tapping a tab switches to that
 * session, long pressing it renames or closes it, and the button at the end adds a new session.
 *
 * The tabs follow the sessions of the {@link TermuxService}, so they are updated from
 * {@link TermuxActivity#updateTerminalTabs()} whenever the sessions, their titles or the current
 * session change.
 */
public class TerminalTabsController {

    private static final int MAX_TITLE_LENGTH = 20;

    private final TermuxActivity mActivity;
    private final View mBar;
    private final TabLayout mTabLayout;

    /** The sessions and titles the tabs were last built with, so they are only rebuilt when needed. */
    private final List<TerminalSession> mShownSessions = new ArrayList<>();
    private final List<String> mShownTitles = new ArrayList<>();

    /** Set while the tabs are changed by code, so that the selection listener does not switch sessions. */
    private boolean mUpdating;

    public TerminalTabsController(@NonNull TermuxActivity activity, @NonNull View bar,
                                  @NonNull TabLayout tabLayout, @NonNull View newSessionButton) {
        mActivity = activity;
        mBar = bar;
        mTabLayout = tabLayout;

        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (mUpdating) return;
                TerminalSession session = sessionAt(tab.getPosition());
                if (session != null) {
                    mActivity.getTermuxTerminalSessionClient().setCurrentSession(session);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        newSessionButton.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().addNewSession(false, null));
    }

    /** Make the tabs match the sessions of the service and select the current session. */
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
            sessions.add(termuxSession.getTerminalSession());
            titles.add(titleOf(termuxSession.getTerminalSession(), sessions.size()));
        }

        mUpdating = true;
        try {
            if (!sessions.equals(mShownSessions) || !titles.equals(mShownTitles)) {
                rebuild(sessions, titles);
            }

            int selected = sessions.indexOf(mActivity.getCurrentSession());
            if (selected >= 0 && mTabLayout.getSelectedTabPosition() != selected) {
                TabLayout.Tab tab = mTabLayout.getTabAt(selected);
                if (tab != null) tab.select();
            }
        } finally {
            mUpdating = false;
        }
    }

    private void rebuild(List<TerminalSession> sessions, List<String> titles) {
        mTabLayout.removeAllTabs();
        for (String title : titles) {
            mTabLayout.addTab(mTabLayout.newTab().setText(title), false);
        }

        mShownSessions.clear();
        mShownSessions.addAll(sessions);
        mShownTitles.clear();
        mShownTitles.addAll(titles);

        // The tab views are created by the layout, so the long press listener is set on them after the rebuild.
        ViewGroup strip = (ViewGroup) mTabLayout.getChildAt(0);
        for (int i = 0; i < strip.getChildCount(); i++) {
            final int index = i;
            strip.getChildAt(i).setOnLongClickListener(v -> {
                showTabMenu(index);
                return true;
            });
        }
    }

    @Nullable
    private TerminalSession sessionAt(int index) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        TermuxSession termuxSession = service.getTermuxSession(index);
        return termuxSession == null ? null : termuxSession.getTerminalSession();
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

    private void showTabMenu(int index) {
        final TerminalSession session = sessionAt(index);
        if (session == null) return;

        new ContextActionSheet(mActivity)
            .addRow(R.string.action_rename_tab, true, () -> mActivity.getTermuxTerminalSessionClient().renameSession(session))
            .addRow(R.string.action_close_tab, true, () -> closeTab(session))
            .show();
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

}
