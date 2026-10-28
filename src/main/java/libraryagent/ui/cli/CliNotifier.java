package libraryagent.ui.cli;

import java.io.PrintStream;
import libraryagent.controller.AgentController;
import libraryagent.notification.LibraryEvent;
import libraryagent.notification.LibraryEventListener;

/** Observer: prints library events for the member logged in to this CLI session. */
public class CliNotifier implements LibraryEventListener {

    private final PrintStream out;
    private final AgentController controller;

    public CliNotifier(PrintStream out, AgentController controller) {
        this.out = out;
        this.controller = controller;
    }

    @Override
    public void onEvent(LibraryEvent event) {
        boolean forCurrentMember = controller.currentMember()
                .map(m -> m.getMemberId().equals(event.memberId()))
                .orElse(false);
        if (forCurrentMember) {
            String label = switch (event.type()) {
                case HOLD_READY -> "Hold ready";
                case OVERDUE -> "Overdue";
            };
            out.println("[Notice: " + label + "] " + event.message());
        }
    }
}
