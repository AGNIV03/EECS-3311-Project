package libraryagent.ui.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;
import libraryagent.controller.AgentController;
import libraryagent.controller.SessionContext;
import libraryagent.notification.LibraryEvent;
import libraryagent.notification.LibraryEventListener;

/**
 * One browser session: its own login, undo history, agent conversation and queue of notices.
 * Requests from the same browser are handled one at a time.
 */
final class WebSession implements LibraryEventListener {

    private static final int MAX_NOTICES = 50;

    final String token;
    final SessionContext context;
    final AgentController controller;
    final ReentrantLock lock = new ReentrantLock();
    private final Deque<Map<String, Object>> notices = new ConcurrentLinkedDeque<>();
    volatile Instant lastSeen;

    WebSession(String token, SessionContext context, AgentController controller, Instant now) {
        this.token = token;
        this.context = context;
        this.controller = controller;
        this.lastSeen = now;
    }

    /** Observer: keeps events addressed to the member signed in to this session. */
    @Override
    public void onEvent(LibraryEvent event) {
        boolean forMe = context.getCurrentMember()
                .map(m -> m.getMemberId().equals(event.memberId()))
                .orElse(false);
        if (forMe) {
            Map<String, Object> notice = new LinkedHashMap<>();
            notice.put("type", event.type().name());
            notice.put("message", event.message());
            notices.addLast(notice);
            while (notices.size() > MAX_NOTICES) {
                notices.pollFirst();
            }
        }
    }

    /** Returns and clears the pending notices. */
    List<Map<String, Object>> drainNotices() {
        List<Map<String, Object>> drained = new ArrayList<>();
        Map<String, Object> next;
        while ((next = notices.pollFirst()) != null) {
            drained.add(next);
        }
        return drained;
    }
}
