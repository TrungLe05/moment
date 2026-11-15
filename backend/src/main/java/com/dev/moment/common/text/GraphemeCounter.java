package com.dev.moment.common.text;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * Đếm "ký tự hiển thị" theo grapheme (BR-11): một emoji hay một chữ có dấu tính là 1.
 * Không dùng String.length() vì nó đếm UTF-16 code unit.
 */
public final class GraphemeCounter {

    private GraphemeCounter() {
    }

    public static int count(CharSequence text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text.toString());
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
