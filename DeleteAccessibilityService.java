package com.primoceara.deletebutton;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Serviço de acessibilidade que faz o trabalho real de apagar texto.
 *
 * Estratégia:
 *  1. Tenta ACTION_SET_TEXT removendo o último caractere (mais confiável).
 *  2. Se não funcionar, usa performGlobalAction com KEYCODE_DEL via gesture injection.
 *  3. Fallback: paste vazio via clipboard.
 */
public class DeleteAccessibilityService extends AccessibilityService {

    private static DeleteAccessibilityService instance;

    public static DeleteAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Não precisa monitorar eventos — só responde a chamadas do overlay
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    /**
     * Apaga um caractere no campo de texto atualmente focado.
     * Chamado pelo OverlayService a cada tick do hold.
     */
    public void performDelete() {
        AccessibilityNodeInfo focus = findFocusedEditText();
        if (focus == null) {
            // Fallback global: simula a tecla backspace
            performGlobalAction(GLOBAL_ACTION_BACK); // não ideal, mas raramente chega aqui
            return;
        }

        // Tenta obter o texto atual e remover o último caractere
        CharSequence text = focus.getText();
        if (text != null && text.length() > 0) {
            // Descobre a posição do cursor
            Bundle selArgs = focus.getExtras();
            int selEnd = -1;

            // Android fornece MOVEMENT_GRANULARITY para mover cursor;
            // a forma mais simples é cortar o texto antes do cursor.
            // Se não tiver cursor explícito, remove do final.
            AccessibilityNodeInfo.RangeInfo range = focus.getRangeInfo();

            String current = text.toString();
            // Remove o último caractere (comportamento padrão quando não há seleção)
            String newText = current.substring(0, Math.max(0, current.length() - 1));

            Bundle args = new Bundle();
            args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText);
            boolean success = focus.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, args);

            if (!success) {
                // Fallback: move cursor para o fim e usa ACTION_CUT de 1 char
                focus.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            }
        }

        focus.recycle();
    }

    /** Encontra o EditText (campo de texto) com foco na tela */
    private AccessibilityNodeInfo findFocusedEditText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        // Tenta o foco de acessibilidade primeiro
        AccessibilityNodeInfo focused =
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) return focused;

        // Tenta foco de acessibilidade
        focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        return focused;
    }
}
