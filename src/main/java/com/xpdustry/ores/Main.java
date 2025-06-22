/*
 * This file is part of Limited Ores plugin for Mindustry.
 *
 * MIT License
 *
 * Copyright (c) 2025 xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.ores;


import arc.struct.Seq;
import arc.util.CommandHandler;

import mindustry.gen.Player;


public class Main extends mindustry.mod.Plugin {
  public static final transient Seq<Player> viewingQuantities = new Seq<>();
  public static final java.util.concurrent.ExecutorService exec = arc.util.Threads.executor(1);
  
  public void init() {
    LimitedOres.init();
    
    arc.util.Threads.daemon("LimitedOresQuantityViewerDaemon", () -> {
     try {
       while (true) {
         Thread.sleep(3000);
         LimitedOres.ores.each((t, r) -> {
           viewingQuantities.each(p ->
             mindustry.gen.Call.labelReliable(p.con, "[#"+r.item.color+"]"+r.quantity, 3, t.worldx(), t.worldy()));
         });
         
       }
     } catch (InterruptedException ignored) {}
    });
  }
  
  public void registerServerCommands(CommandHandler handler) {
    //TODO reload command
    
  }
  
  public void registerClientCommands(CommandHandler handler) {
    handler.<Player>register("view-quantity", "See remaining ore quantity per tile", (args, player) -> {
      if (!player.admin) player.sendMessage("[scarlet]You must be an admin to use this command.");
      else if (!viewingQuantities.addUnique(player)) 
        viewingQuantities.remove(player);  
    });
  }
}
