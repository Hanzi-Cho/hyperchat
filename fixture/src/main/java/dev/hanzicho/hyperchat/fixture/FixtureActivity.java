package dev.hanzicho.hyperchat.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class FixtureActivity extends Activity {
    private LinearLayout layout;
    private EditText input;
    private TextView results;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int inset = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(inset, inset * 2, inset, inset);
        setContentView(layout);
        TextView banner = new TextView(this);
        banner.setText("테스트 지도 · 네이버지도 아님");
        banner.setTextSize(22);
        layout.addView(banner);
        Button entry = new Button(this);
        entry.setId(R.id.search_entry);
        entry.setText("장소 검색");
        layout.addView(entry);
        entry.setOnClickListener(view -> openSearch(entry));
    }

    private void openSearch(Button entry) {
        layout.removeView(entry);
        input = new EditText(this);
        input.setId(R.id.search_input);
        input.setSingleLine(true);
        input.setHint("장소 검색");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button submit = new Button(this);
        submit.setId(R.id.search_submit);
        submit.setText("검색");
        layout.addView(submit);
        results = new TextView(this);
        results.setId(R.id.search_results);
        results.setTextSize(22);
        layout.addView(results);
        submit.setOnClickListener(view -> showResults());
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            showResults();
            return true;
        });
    }

    private void showResults() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) return;
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
        results.setText("검색 결과: " + query);
    }
}
