package com.nxenon.grpcweb.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.RawEditor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Send-to… stand-in for Burp's missing native message context menu on custom tabs.
 */
class EditorSendToMenuTest {

    private MontoyaApi api;
    private HttpRequest request;

    @BeforeEach
    void setUp() {
        BurpTestHarness.installObjectFactory();
        api = mock(MontoyaApi.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        request = mock(HttpRequest.class);
        when(request.toByteArray()).thenReturn(bytes("GET / HTTP/1.1\r\n\r\n"));
    }

    private static ByteArray bytes(String value) {
        return new BurpTestHarness.TestByteArray(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> labels(JPopupMenu menu) {
        List<String> labels = new ArrayList<>();
        for (Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item) {
                labels.add(item.getText());
            }
        }
        return labels;
    }

    @Nested
    class MenuContents {

        @Test
        @DisplayName("a request yields Send to Intruder, Repeater, Organizer, Comparer and Decoder")
        void requestMenuHasSendToItems() {
            EditorSendToMenu menu = EditorSendToMenu.forRequest(api, () -> request, () -> null);
            List<String> labels = labels(menu.buildMenu());

            assertTrue(labels.contains("Send to Intruder"), labels.toString());
            assertTrue(labels.contains("Send to Repeater"), labels.toString());
            assertTrue(labels.contains("Send to Organizer"), labels.toString());
            assertTrue(labels.contains("Send to Comparer"), labels.toString());
            assertTrue(labels.contains("Send to Decoder"), labels.toString());
            assertTrue(labels.contains("Copy request"), labels.toString());
        }

        @Test
        @DisplayName("with no request and no selection the menu is empty")
        void emptyWhenNothingToSend() {
            EditorSendToMenu menu = EditorSendToMenu.forRequest(api, () -> null, () -> null);
            assertEquals(0, menu.buildMenu().getComponentCount());
        }

        @Test
        @DisplayName("a selection is preferred for Comparer and Decoder")
        void selectionPreferredForComparer() {
            ByteArray selection = bytes("selected");
            EditorSendToMenu menu = EditorSendToMenu.forRequest(api, () -> request, () -> selection);

            JPopupMenu popup = menu.buildMenu();
            JMenuItem comparer = find(popup, "Send to Comparer");
            comparer.doClick();

            verify(api.comparer()).sendToComparer(selection);
        }

        @Test
        @DisplayName("Send to Repeater forwards the current request")
        void sendToRepeaterUsesRequest() {
            EditorSendToMenu menu = EditorSendToMenu.forRequest(api, () -> request, () -> null);
            find(menu.buildMenu(), "Send to Repeater").doClick();
            verify(api.repeater()).sendToRepeater(request);
        }

        @Test
        @DisplayName("Send to Intruder forwards the current request")
        void sendToIntruderUsesRequest() {
            EditorSendToMenu menu = EditorSendToMenu.forRequest(api, () -> request, () -> null);
            find(menu.buildMenu(), "Send to Intruder").doClick();
            verify(api.intruder()).sendToIntruder(request);
        }
    }

    @Nested
    class Installation {

        @Test
        @DisplayName("installOn attaches a listener to a nested syntaxTextArea")
        void installsOnNamedTextArea() {
            JTextArea textArea = new JTextArea();
            textArea.setName("syntaxTextArea");
            JPanel root = new JPanel();
            root.add(textArea);

            RawEditor editor = mock(RawEditor.class);
            when(editor.uiComponent()).thenReturn(root);

            int before = textArea.getMouseListeners().length;
            EditorSendToMenu.forRequest(api, () -> request, () -> null).installOn(editor);
            assertTrue(textArea.getMouseListeners().length > before);
        }

        @Test
        @DisplayName("installOn falls back to the editor root when no text area exists")
        void fallsBackToRoot() {
            JPanel root = new JPanel();
            RawEditor editor = mock(RawEditor.class);
            when(editor.uiComponent()).thenReturn(root);

            int before = root.getMouseListeners().length;
            EditorSendToMenu.forRequest(api, () -> request, () -> null).installOn(editor);
            assertTrue(root.getMouseListeners().length > before);
        }

        @Test
        @DisplayName("request editor construction installs the menu without throwing")
        void requestEditorInstallsCleanly() {
            var editors = BurpTestHarness.editorMap();
            MontoyaApi harnessApi = BurpTestHarness.api(editors);
            ExtensionSettings settings = new ExtensionSettings(new FakePreferences());

            GrpcWebRequestEditor editor = new GrpcWebRequestEditor(
                    harnessApi, settings, BurpTestHarness.creationContext(
                            burp.api.montoya.ui.editor.extension.EditorMode.DEFAULT));
            editor.setRequestResponse(BurpTestHarness.requestWith(
                    "application/grpc-web-text", "AAAAAAkKBGFtaW4QuQo="));

            assertFalse(editor.getRequest() == null);
        }
    }

    @Nested
    class ComponentLookup {

        @Test
        @DisplayName("editableSurface finds a nested syntaxTextArea by name")
        void findsNamedArea() {
            JTextArea textArea = new JTextArea();
            textArea.setName("syntaxTextArea");
            JPanel nested = new JPanel();
            nested.add(textArea);
            JPanel root = new JPanel();
            root.add(nested);

            RawEditor editor = mock(RawEditor.class);
            when(editor.uiComponent()).thenReturn(root);

            assertEquals(textArea, BurpEditorComponents.editableSurface(editor));
        }
    }

    private static JMenuItem find(JPopupMenu menu, String label) {
        for (Component component : menu.getComponents()) {
            if (component instanceof JMenuItem item && label.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("missing menu item: " + label);
    }
}
