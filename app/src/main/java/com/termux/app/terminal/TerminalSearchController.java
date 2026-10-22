package com.termux.app.terminal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search in the scrollback of the current session. The matching lines are found in the screen and
 * its history, the current one is highlighted and scrolled to the middle of the terminal, and the
 * previous and next buttons (or the search key of the keyboard) move through them.
 *
 * The search ignores case. It works per line, so a match that wraps over two screen lines is not
 * found, and a line counts once however many times the text is in it.
 */
public class TerminalSearchController {

    private static final int NONE = Integer.MIN_VALUE;

    /** How long to wait after the last typed character before searching, a search goes over the whole history. */
    private static final int SEARCH_DELAY_MS = 200;

    private final TermuxActivity mActivity;
    private final View mBar;
    private final View mTabsBar;
    private final EditText mInput;
    private final TextView mCount;
    private final float mDensity;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mSearchLater = () -> {
        mCurrentRow = NONE;
        search();
    };

    /** The row of the current match, rows in the history are negative. */
    private int mCurrentRow = NONE;
    @Nullable private TerminalSession mSearchedSession;

    public TerminalSearchController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mBar = activity.findViewById(R.id.terminal_search_bar);
        mTabsBar = activity.findViewById(R.id.terminal_tabs_bar);
        mInput = activity.findViewById(R.id.terminal_search_input);
        mCount = activity.findViewById(R.id.terminal_search_count);
        mDensity = activity.getResources().getDisplayMetrics().density;

        activity.findViewById(R.id.terminal_search_prev).setOnClickListener(v -> step(-1));
        activity.findViewById(R.id.terminal_search_next).setOnClickListener(v -> step(1));
        activity.findViewById(R.id.terminal_search_close).setOnClickListener(v -> close());

        mInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                mHandler.removeCallbacks(mSearchLater);
                mHandler.postDelayed(mSearchLater, SEARCH_DELAY_MS);
            }
        });
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                step(1);
                return true;
            }
            return false;
        });
    }

    public boolean isOpen() {
        return mBar.getVisibility() == View.VISIBLE;
    }

    public void open() {
        // The bar sits right under the tabs, or at the top of the screen when there are no tabs.
        int top = mTabsBar.getVisibility() == View.VISIBLE ? 0 : Math.round(6 * mDensity);
        mBar.setPadding(mBar.getPaddingLeft(), top, mBar.getPaddingRight(), mBar.getPaddingBottom());
        mBar.setVisibility(View.VISIBLE);

        mInput.requestFocus();
        mInput.selectAll();
        // The terminal view hides the keyboard when it loses focus, so ask for it a bit later.
        mInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && isOpen()) imm.showSoftInput(mInput, InputMethodManager.SHOW_IMPLICIT);
        }, 150);

        mCurrentRow = NONE;
        search();
    }

    public void close() {
        if (!isOpen()) return;
        mHandler.removeCallbacks(mSearchLater);
        mBar.setVisibility(View.GONE);
        mCount.setText("");
        mCurrentRow = NONE;
        mSearchedSession = null;

        TerminalView view = mActivity.getTerminalView();
        view.setHighlightedRow(Integer.MIN_VALUE);

        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
        view.requestFocus();
    }

    /** The session changed, so search it again if the bar is open. */
    public void onSessionChanged() {
        if (!isOpen() || mActivity.getCurrentSession() == mSearchedSession) return;
        mCurrentRow = NONE;
        search();
    }

    /** Find the matches, and show the one nearest to the bottom, or the current one if it is still found. */
    private void search() {
        move(0);
    }

    private void step(int direction) {
        move(direction);
    }

    /**
     * @param direction 0 to show the current match (the latest one if there is none yet), 1 for the
     *                  next match further down, -1 for the previous one further up.
     */
    private void move(int direction) {
        TerminalView view = mActivity.getTerminalView();
        TerminalSession session = mActivity.getCurrentSession();
        mSearchedSession = session;

        List<Integer> rows = findRows(session, mInput.getText().toString());
        if (rows.isEmpty()) {
            view.setHighlightedRow(Integer.MIN_VALUE);
            mCurrentRow = NONE;
            mCount.setText(mInput.getText().length() == 0 ? "" : "0/0");
            return;
        }

        int index;
        if (mCurrentRow == NONE) {
            index = rows.size() - 1;
        } else if (direction > 0) {
            index = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) > mCurrentRow) { index = i; break; }
            }
        } else if (direction < 0) {
            index = rows.size() - 1;
            for (int i = rows.size() - 1; i >= 0; i--) {
                if (rows.get(i) < mCurrentRow) { index = i; break; }
            }
        } else {
            index = rows.indexOf(mCurrentRow);
            if (index < 0) index = rows.size() - 1;
        }

        mCurrentRow = rows.get(index);
        mCount.setText(String.format(Locale.getDefault(), "%d/%d", index + 1, rows.size()));
        view.setHighlightedRow(mCurrentRow);
        view.scrollToRow(mCurrentRow);
    }

    /** The rows of the history and the screen, oldest first, that contain the text. */
    @NonNull
    private List<Integer> findRows(@Nullable TerminalSession session, @NonNull String query) {
        List<Integer> rows = new ArrayList<>();
        if (session == null || query.isEmpty()) return rows;
        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) return rows;

        TerminalBuffer screen = emulator.getScreen();
        String needle = query.toLowerCase(Locale.ROOT);
        int first = -screen.getActiveTranscriptRows();
        for (int row = first; row < emulator.mRows; row++) {
            String text = screen.getSelectedText(0, row, emulator.mColumns, row, false);
            if (text.toLowerCase(Locale.ROOT).contains(needle)) rows.add(row);
        }
        return rows;
    }
}
