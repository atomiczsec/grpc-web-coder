package com.nxenon.grpcweb.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Closest supported stand-in for Burp's message-editor right-click menu inside a custom editor tab.
 *
 * <p>Montoya's {@code createRawEditor()} is Burp's own text editor and gives search, theme and
 * cut/copy/paste, but it does <em>not</em> attach the full HTTP message context menu (Send to
 * Intruder / Repeater / Comparer / Decoder, Copy as…, Extensions) when the editor is hosted by an
 * {@code ExtensionProvidedHttpRequestEditor}. There is no Montoya API to opt into that menu —
 * PortSwigger's docs for {@code createRawEditor} list search/theme/bytes only, and production
 * extensions such as InQL reimplement Send-to the same way for their custom GraphQL tab.
 *
 * <p>This menu wires the public {@code sendTo*} APIs to the request (or response) currently shown
 * in the tab, including any edits the user has made in the decoded view.
 */
final class EditorSendToMenu extends MouseAdapter {

    private final MontoyaApi api;
    private final Supplier<HttpRequest> requestSupplier;
    private final Supplier<HttpResponse> responseSupplier;
    private final Supplier<ByteArray> selectionSupplier;
    private final Supplier<Clipboard> clipboardSupplier;

    EditorSendToMenu(
            MontoyaApi api,
            Supplier<HttpRequest> requestSupplier,
            Supplier<HttpResponse> responseSupplier,
            Supplier<ByteArray> selectionSupplier) {
        this(api, requestSupplier, responseSupplier, selectionSupplier,
                () -> Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    EditorSendToMenu(
            MontoyaApi api,
            Supplier<HttpRequest> requestSupplier,
            Supplier<HttpResponse> responseSupplier,
            Supplier<ByteArray> selectionSupplier,
            Supplier<Clipboard> clipboardSupplier) {
        this.api = api;
        this.requestSupplier = requestSupplier;
        this.responseSupplier = responseSupplier;
        this.selectionSupplier = selectionSupplier;
        this.clipboardSupplier = clipboardSupplier;
    }

    /** Attaches this menu to the editable surface of a Burp raw editor. */
    void installOn(RawEditor editor) {
        Component surface = BurpEditorComponents.editableSurface(editor);
        if (surface != null) {
            surface.addMouseListener(this);
        }
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (event.isPopupTrigger()) {
            showMenu(event);
        }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (event.isPopupTrigger()) {
            showMenu(event);
        }
    }

    private void showMenu(MouseEvent event) {
        JPopupMenu menu = buildMenu();
        if (menu.getComponentCount() == 0) {
            return;
        }
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        HttpRequest request = safeGet(requestSupplier);
        HttpResponse response = safeGet(responseSupplier);
        ByteArray selection = safeGet(selectionSupplier);

        if (request != null) {
            menu.add(item("Send to Intruder", () -> api.intruder().sendToIntruder(request)));
            menu.add(item("Send to Repeater", () -> api.repeater().sendToRepeater(request)));
            menu.add(item("Send to Organizer", () -> api.organizer().sendToOrganizer(request)));
        }

        ByteArray comparerData = selectionOrMessage(selection, request, response);
        if (comparerData != null && comparerData.length() > 0) {
            if (menu.getComponentCount() > 0) {
                menu.addSeparator();
            }
            menu.add(item("Send to Comparer",
                    () -> api.comparer().sendToComparer(comparerData)));
            menu.add(item("Send to Decoder",
                    () -> api.decoder().sendToDecoder(comparerData)));
        }

        if (request != null || selection != null) {
            if (menu.getComponentCount() > 0) {
                menu.addSeparator();
            }
            if (selection != null && selection.length() > 0) {
                menu.add(item("Copy", () -> copy(selection, StandardCharsets.UTF_8)));
            } else if (request != null) {
                menu.add(item("Copy request",
                        () -> copy(request.toByteArray(), StandardCharsets.ISO_8859_1)));
            }
        }

        return menu;
    }

    private static ByteArray selectionOrMessage(
            ByteArray selection, HttpRequest request, HttpResponse response) {
        if (selection != null && selection.length() > 0) {
            return selection;
        }
        if (response != null) {
            return response.toByteArray();
        }
        if (request != null) {
            return request.toByteArray();
        }
        return null;
    }

    /**
     * Puts bytes on the text clipboard.
     *
     * <p>A selection comes from the decoded payload, which is UTF-8 JSON. A whole request is raw
     * HTTP whose body may be binary gRPC frames, so it is copied as ISO-8859-1: every byte maps to
     * exactly one character, and pasting it back yields the original bytes. UTF-8 would replace
     * each invalid byte with U+FFFD.
     */
    private void copy(ByteArray data, Charset charset) {
        String text = new String(data.getBytes(), charset);
        clipboardSupplier.get().setContents(new StringSelection(text), null);
    }

    private static JMenuItem item(String label, Runnable action) {
        JMenuItem menuItem = new JMenuItem(label);
        menuItem.addActionListener(event -> action.run());
        return menuItem;
    }

    private static <T> T safeGet(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Selection bytes from a raw editor, or {@code null} when nothing is selected.
     *
     * <p>Exposed for tests and for callers that want the same selection Burp would pass to
     * Comparer / Decoder.
     */
    static ByteArray selectedBytes(RawEditor editor) {
        if (editor == null) {
            return null;
        }
        return editor.selection()
                .map(Selection::contents)
                .orElse(null);
    }

    /** Convenience when only a request is available (request editor tab). */
    static EditorSendToMenu forRequest(
            MontoyaApi api, Supplier<HttpRequest> request, Supplier<ByteArray> selection) {
        return new EditorSendToMenu(api, request, () -> null, selection);
    }
}
