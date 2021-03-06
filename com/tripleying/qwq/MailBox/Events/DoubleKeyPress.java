package com.tripleying.qwq.MailBox.Events;

import lk.vexview.event.KeyBoardPressEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class DoubleKeyPress implements Listener {
    
    public DoubleKeyPress(int key1, int key2){
        this.key1 = key1;
        this.key2 = key2;
    }
    
    private final int key1;
    private final int key2;
    
    private boolean canOpen = false;
    
    @EventHandler
    public void openMailBox(KeyBoardPressEvent e){
        // 按Ctrl+M打开邮箱GUI
        if(canOpen&&e.getKey()==key2){
            e.getPlayer().performCommand("mailbox");
            canOpen = false;
        }
        if(e.getKey()==key1){
            canOpen = e.getEventKeyState();
        }
    }
    
}
