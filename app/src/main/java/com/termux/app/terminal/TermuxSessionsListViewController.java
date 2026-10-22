package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    /** The theme defined title and subtitle text colors, used when a session has not failed. */
    final int mDefaultTitleColor;
    final int mDefaultSubtitleColor;

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
        this.mDefaultTitleColor = resolveThemeColor(R.attr.termuxSessionItemTitleColor, Color.BLACK);
        this.mDefaultSubtitleColor = resolveThemeColor(R.attr.termuxSessionItemSubtitleColor, Color.DKGRAY);
    }

    private int resolveThemeColor(int attr, int fallback) {
        TypedValue typedValue = new TypedValue();
        if (mActivity.getTheme().resolveAttribute(attr, typedValue, true) &&
            typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return typedValue.data;
        }
        return fallback;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);
        TextView sessionSubtitleView = sessionRowView.findViewById(R.id.session_subtitle);
        View sessionStatusView = sessionRowView.findViewById(R.id.session_status);

        TerminalSession sessionAtRow = getItem(position).getTerminalSession();
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            sessionSubtitleView.setVisibility(View.GONE);
            return sessionRowView;
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        boolean hasName = !TextUtils.isEmpty(name);
        boolean hasTitle = !TextUtils.isEmpty(sessionTitle);

        // First line is "[n] name", or "[n] title" if the session has not been named.
        String numberPart = "[" + (position + 1) + "] ";
        sessionTitleView.setText(numberPart + (hasName ? name : (hasTitle ? sessionTitle : "")));

        // Second line is the terminal title (usually the running program) if the session has a name.
        if (hasName && hasTitle) {
            sessionSubtitleView.setText(sessionTitle);
            sessionSubtitleView.setVisibility(View.VISIBLE);
        } else {
            sessionSubtitleView.setText("");
            sessionSubtitleView.setVisibility(View.GONE);
        }

        boolean sessionRunning = sessionAtRow.isRunning();
        boolean sessionFailed = !sessionRunning && sessionAtRow.getExitStatus() != 0;

        int strikeFlag = sessionRunning ? 0 : Paint.STRIKE_THRU_TEXT_FLAG;
        sessionTitleView.setPaintFlags((sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG) | strikeFlag);
        sessionSubtitleView.setPaintFlags((sessionSubtitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG) | strikeFlag);

        int errorColor = MaterialColors.getColor(mActivity, com.google.android.material.R.attr.colorError, Color.RED);
        sessionTitleView.setTextColor(sessionFailed ? errorColor : mDefaultTitleColor);
        sessionSubtitleView.setTextColor(sessionFailed ? errorColor : mDefaultSubtitleColor);

        // Status dot: green while running, grey when finished successfully, red when failed.
        int statusColorRes = sessionRunning ? R.color.session_status_running :
            (sessionFailed ? R.color.session_status_failed : R.color.session_status_finished);
        sessionStatusView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mActivity, statusColorRes)));

        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }

}
