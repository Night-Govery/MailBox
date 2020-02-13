package com.tripleying.qwq.MailBox.Mail;

import com.tripleying.qwq.MailBox.API.Listener.*;
import com.tripleying.qwq.MailBox.API.MailBoxAPI;
import com.tripleying.qwq.MailBox.GlobalConfig;
import com.tripleying.qwq.MailBox.Message;
import com.tripleying.qwq.MailBox.Utils.DateTime;
import com.tripleying.qwq.MailBox.Utils.Reflection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BaseFileMail extends BaseMail {
    
    // 附件名
    private String fileName;
    // 附件是否启用指令
    private boolean hasCommand;
    // 指令列表
    private List<String> commandList;
    // 指令描述
    private List<String> commandDescription;
    // 附件是否含有物品
    private boolean hasItem;
    // 物品列表
    private ArrayList<ItemStack> itemList;
    // 附件经验（未实现）
    private float exp;
    // 附件金币
    private double coin;
    // 附件点券
    private int point;
    
    public BaseFileMail(String type, int id, String sender, String topic, String content, String date, String filename){
        super(type, id, sender, topic, content, date);
        this.fileName = filename;
        readFile();
    }
    
    public BaseFileMail(String type, int id, String sender, String topic, String content, String date, String filename, ArrayList<ItemStack> isl, List<String> cl, List<String> cd, double coin, int point){
        super(type, id, sender, topic, content, date);
        this.fileName = filename;
        this.itemList = isl;
        this.commandList = cl;
        this.commandDescription = cd;
        this.hasItem = !isl.isEmpty();
        this.hasCommand = !cl.isEmpty();
        this.coin = coin;
        this.point = point;
    }
    
    @Override
    public boolean Collect(Player p){
        if(!collectValidate(p)) return false;
        // 判断背包空间
        if(hasItem && !hasBlank(p)){
            p.sendMessage(Message.itemInvNotEnough);
            return false;
        }
        // 设置玩家领取邮件
        if(MailBoxAPI.setCollect(getType(), getId(), p.getName())){
            // 发送邮件附件
            if(hasItem) giveItem(p);
            // 执行邮件指令
            if(hasCommand) doCommand(p);
            // 给钱
            if(coin!=0 && giveCoin(p, coin)) p.sendMessage(Message.moneyBalanceAdd.replace("%money%", Message.moneyVault).replace("%count%", Double.toString(coin)));
            if(point!=0 && givePoint(p, point)) p.sendMessage(Message.moneyBalanceAdd.replace("%money%", Message.moneyPlayerpoints).replace("%count%", Integer.toString(point)));
            MailCollectEvent mce = new MailCollectEvent(this, p);
            Bukkit.getServer().getPluginManager().callEvent(mce);
            p.sendMessage(Message.mailCollectSuccess);
            Bukkit.getConsoleSender().sendMessage(Message.mailCollect.replace("%player%", p.getName()).replace("%type%", getTypeName()).replace("%id%", Integer.toString(getId())));
            return true;
        }else{
            p.sendMessage(Message.mailCollectError);
            return false;
        }
    }
    
    @Override
    public boolean Send(CommandSender send, ConversationContext cc){
        if(send==null) return false;
        if(getId()==0){
            if(send instanceof Player){
                Player p = (Player)send;
                if(!sendValidate(p, null)) return false;
                // 新建邮件
                // 判断玩家背包里是否有想要发送的物品
                if(hasItem && !p.hasPermission("mailbox.admin.send.check.item")){
                    if(!hasItem(getTrueItemList(), p, cc)){
                        return false;
                    }
                }
                double needCoin = getExpandCoin();
                int needPoint = getExpandPoint();
                if(!enoughMoney(p,needCoin,needPoint,cc)) return false;
                // 获取时间
                generateDate();
                try {
                    // 生成一个文件名
                    fileName = MailBoxAPI.generateFilename(getType());
                }catch (Exception ex) {
                    if(cc==null){
                        p.sendMessage(Message.mailFileNameError);
                    }else{
                        cc.getForWhom().sendRawMessage(Message.mailFileNameError);
                    }
                    return false;
                }
                if(saveFile()){
                    if(!sendValidate(p, null)){
                        DeleteFile();
                        return false;
                    }
                    if(!enoughMoney(p,needCoin,needPoint,cc)){
                        DeleteFile();
                        return false;
                    }
                    // 删除玩家背包里想要发送的物品
                    if(removeItem(getTrueItemList(), p, cc)){
                        if(sendData()){
                            // 扣钱
                            if(needCoin!=0 && !p.hasPermission("mailbox.admin.send.noconsume.coin") && removeCoin(p, needCoin)){
                                if(cc==null){
                                    p.sendMessage(Message.mailExpand.replace("%type%", Message.moneyVault).replace("%count%", Double.toString(needCoin)));
                                }else{
                                    cc.getForWhom().sendRawMessage(Message.mailExpand.replace("%type%", Message.moneyVault).replace("%count%", Double.toString(needCoin)));
                                }
                            }
                            if(needPoint!=0 && !p.hasPermission("mailbox.admin.send.noconsume.point") && removePoint(p, needPoint)){
                                if(cc==null){
                                    p.sendMessage(Message.mailExpand.replace("%type%", Message.moneyPlayerpoints).replace("%count%", Integer.toString(needPoint)));
                                }else{
                                    cc.getForWhom().sendRawMessage(Message.mailExpand.replace("%type%", Message.moneyPlayerpoints).replace("%count%", Integer.toString(needPoint)));
                                }
                            }
                            MailSendEvent mse = new MailSendEvent(this, p);
                            Bukkit.getServer().getPluginManager().callEvent(mse);
                            return true;
                        }else{
                            if(cc==null){
                                p.sendMessage(Message.mailSendSqlError);
                            }else{
                                cc.getForWhom().sendRawMessage(Message.mailSendSqlError);
                            }
                            return false;
                        }
                    }else{
                        DeleteFile();
                        return false;
                    }
                }else{
                    StringBuilder str = new StringBuilder(Message.mailFileSaveError);
                    if(p.isOp()) str.append(", ").append(Message.fileFilename).append(": ").append(fileName);
                    if(cc==null){
                        p.sendMessage(str.toString());
                    }else{
                        cc.getForWhom().sendRawMessage(str.toString());
                    }
                    return false;
                }
            }else{
                if(!getType().equals("date") || getDate().equals("0")) setDate(DateTime.get("ymdhms"));
                try {
                    // 生成一个文件名
                    fileName = MailBoxAPI.generateFilename(getType());
                }catch (Exception ex) {
                    if(cc==null){
                        send.sendMessage(Message.mailFileNameError);
                    }else{
                        cc.getForWhom().sendRawMessage(Message.mailFileNameError);
                    }
                    return false;
                }
                if(saveFile()){
                    if(sendData()){
                        MailSendEvent mse = new MailSendEvent(this, send);
                        Bukkit.getServer().getPluginManager().callEvent(mse);
                        return true;
                    }else{
                        if(cc==null){
                            send.sendMessage(Message.mailSendSqlError);
                        }else{
                            cc.getForWhom().sendRawMessage(Message.mailSendSqlError);
                        }
                        return false;
                    }
                }else{
                    StringBuilder str = new StringBuilder(Message.mailFileSaveError);
                    if(send.isOp()) str.append(", ").append(Message.fileFilename).append(": ").append(fileName);
                    if(cc==null){
                        send.sendMessage(str.toString());
                    }else{
                        cc.getForWhom().sendRawMessage(str.toString());
                    }
                    return false;
                }
            }
        }else{
            //TODO 修改已有邮件
            return false;
        }
    }
    @Override
    public boolean Delete(Player p){
        if(DeleteFile()){
            return DeleteData(p);
        }else{
            return false;
        }
    }
    // 删除这封邮件的附件
    public boolean DeleteFile(){
        return (MailBoxAPI.setDeleteFileSQL(fileName, getType()) | MailBoxAPI.setDeleteFile(fileName, getType()));
    }

    // 执行指令
    public boolean doCommand(Player p) {
        if(commandList!=null){
            List<String> op = new ArrayList();
            for(int i=0;i<commandList.size();i++){
                String cs = commandList.get(i);
                if(cs.endsWith(":op")){
                    op.add(cs.substring(0, cs.length()-3));
                }else{
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cs.replace("%player%", p.getName()));
                }
            }
            if(!op.isEmpty()){
                boolean isOp = p.isOp();
                try{
                    p.setOp(true);
                    op.forEach(opc -> p.performCommand(opc.replace("%player%", p.getName())));
                }finally {
                    p.setOp(isOp);
                }
            }
            return true;
        }else{
            p.sendMessage(Message.extracommandCommandError);
            return false;
        }
    }
    
    public boolean hasBlank(Player p){
        int ils = itemList.size();
        int allAir = 0;
        for(ItemStack it:GlobalConfig.server_under_1_10 ? p.getInventory().getContents() : p.getInventory().getStorageContents()){
            if(it==null){
                if((allAir++)>=ils){
                    return true;
                }
            }
        }
        if(allAir<ils){
            int needAir = 0;
            o:for(int i=0;i<ils;i++){
                ItemStack is1 = itemList.get(i);
                HashMap<Integer, ? extends ItemStack> im = p.getInventory().all(is1.getType());
                if(!im.isEmpty()){
                    Set<Integer> ks = im.keySet();
                    for(Integer k:ks){
                        ItemStack is2 = im.get(k);
                        if(is2.isSimilar(is1) && is2.getAmount()+is1.getAmount()<=is2.getMaxStackSize()){
                            continue o;
                        }
                    }
                }
                needAir++;
            }
            return allAir >= needAir;
        }else{
            return true;
        }
    }

    public boolean giveItem(Player p) {
        BaseComponent[] bc = new BaseComponent[itemList.size()+1];
        bc[0] = new TextComponent(Message.itemItemClaim);
        ItemStack[] isa = new ItemStack[itemList.size()];
        for(int i = 0 ;i<itemList.size();i++){
            isa[i] = MailBoxAPI.randomLore(itemList.get(i));
            HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_ITEM,  new BaseComponent[]{new TextComponent(Reflection.Item2Json(isa[i]))});
            TextComponent component = new TextComponent(" §r"+MailBoxAPI.getItemName(isa[i])+"§8x§r"+isa[i].getAmount());
            component.setHoverEvent(event);
            bc[i+1] = component;
        }
        p.getInventory().addItem(isa);
        p.spigot().sendMessage(bc);
        return true;
    }
    
    // 获取真实发送物品列表
    public ArrayList<ItemStack> getTrueItemList(){
        ItemStack is;
        int amount;
        int size = itemList.size();
        ArrayList<ItemStack> isn = new ArrayList();
        List<Integer> ignore = new ArrayList();
        for(int i=0;i<size;i++){
            if(ignore.contains(i)) continue;
            is = itemList.get(i).clone();
            amount = is.getAmount();
            for(int j=i+1;j<size;j++){
                if(is.isSimilar(itemList.get(j))){
                    ignore.add(j);
                    amount += itemList.get(j).getAmount();
                }
            }
            is.setAmount(amount);
            isn.add(is);
        }
        return isn;
    }
    
    // 判断玩家背包里是否有想要发送的物品
    public boolean hasItem(ArrayList<ItemStack> isl, Player p, ConversationContext cc){
        for(int i=0;i<isl.size();i++){
            if(!p.getInventory().containsAtLeast(isl.get(i), isl.get(i).getAmount())) {
                if(cc==null){
                    p.sendMessage(Message.itemItemNotEnough.replace("%item%", MailBoxAPI.getItemName(isl.get(i))));
                }else{
                    cc.getForWhom().sendRawMessage(Message.itemItemNotEnough.replace("%item%", MailBoxAPI.getItemName(isl.get(i))));
                }
                return false;
            }
        }
        return true;
    }
    
    // 移除玩家背包里想要发送的物品
    public boolean removeItem(ArrayList<ItemStack> isl, Player p, ConversationContext cc){
        if(p.hasPermission("mailbox.admin.send.noconsume.item"))return true;
        boolean success = true;
        ArrayList<Integer> clearList = new ArrayList();
        HashMap<Integer, ItemStack> reduceList = new HashMap();
        String error = "";
        for(int i=0;i<isl.size();i++){
            ItemStack is1 = isl.get(i);
            int count = is1.getAmount();
            for(int j=0;j<36;j++){
                if(p.getInventory().getItem(j)!=null){
                    ItemStack is2 = p.getInventory().getItem(j).clone();
                    if(is1.isSimilar(is2)){
                        int amount = is2.getAmount();
                        if(count<=amount){
                            int temp = amount-count;
                            if(temp==0){
                                clearList.add(j);
                            }else{
                                is2.setAmount(temp);
                                reduceList.put(j, is2);
                            }
                            count = 0;
                            break;
                        }else{
                            clearList.add(j);
                            count -= amount;
                        }
                    }
                }
            }
            if(count!=0){
                success = false;
                error += " "+MailBoxAPI.getItemName(is1)+"x"+count;
            }
        }
        if(success){
            if(!clearList.isEmpty()){
                clearList.forEach(k -> {
                    p.getInventory().clear(k);
                });
            }
            if(!reduceList.isEmpty()){
                reduceList.forEach((k, v) -> {
                    p.getInventory().setItem(k, v);
                });
            }
        }else{
            if(cc==null){
                p.sendMessage(Message.itemItemNotEnough.replace("%item%", error));
            }else{
                cc.getForWhom().sendRawMessage(Message.itemItemNotEnough.replace("%item%", error));
            }
        }
        return success;
    }
    
    public boolean giveCoin(Player p, double coin){
        return MailBoxAPI.addEconomy(p, coin);
    }
    
    @Override
    public double getExpandCoin(){
        if(GlobalConfig.enVault && (coin!=0 || GlobalConfig.vaultExpand!=0 || (hasItem && GlobalConfig.vaultItem!=0))){
            return coin+GlobalConfig.vaultExpand+itemList.size()*GlobalConfig.vaultItem;
        }else{
            return 0;
        }
    }
    
    public boolean givePoint(Player p, int point){
        return MailBoxAPI.addPoints(p, point);
    }
    
    @Override
    public int getExpandPoint(){
        if(GlobalConfig.enPlayerPoints && (point!=0 || GlobalConfig.playerPointsExpand!=0 || (hasItem && GlobalConfig.playerPointsItem!=0))){
            return point+GlobalConfig.playerPointsExpand+itemList.size()*GlobalConfig.playerPointsItem;
        }else{
            return 0;
        }
    }
    
    public void setFileName(String fileName){
        this.fileName = fileName;
    }
    
    public String getFileName(){
        return this.fileName;
    }
    
    public void setCommandList(List<String> commandList){
        this.commandList = commandList;
        this.hasCommand = !commandList.isEmpty();
    }
    
    public boolean isHasCommand(){
        return this.hasCommand;
    }
    
    public List<String> getCommandList(){
        return this.commandList;
    }
    
    public final String getCommandListString(){
        String str = "";
        if(!commandList.isEmpty()){
            str = commandList.stream().map((n) -> "/"+n).reduce(str, String::concat);
            str = str.substring(1);
        }
        return str;
    }
    
    public void setCommandDescription(List<String> commandDescription){
        this.commandDescription = commandDescription;
    }
    
    public List<String> getCommandDescription(){
        return this.commandDescription;
    }
    
    public final String getCommandDescriptionString(){
        String str = "";
        if(!commandDescription.isEmpty()){
            str = commandDescription.stream().map((n) -> " "+n).reduce(str, String::concat);
            str = str.substring(1);
        }
        return str;
    }
    
    public void setItemList(ArrayList<ItemStack> itemList){
        this.itemList = itemList;
        this.hasItem = !itemList.isEmpty();
    }
    
    public List<String> getItemNameList(){
        List<String> l = new ArrayList();
        if(hasItem) itemList.forEach(i -> l.add(MailBoxAPI.getItemName(i)));
        return l;
    }
    
    public String getItemNameString(){
        String str = "";
        str = itemList.stream().map((n) -> " "+MailBoxAPI.getItemName(n)).reduce(str, String::concat);
        if(str.length()>0) str = str.substring(1);
        return str;
    }
    
    public boolean isHasItem(){
        return this.hasItem;
    }
    
    public ArrayList<ItemStack> getItemList(){
        return this.itemList;
    }
    
    public void setCoin(double coin){
        this.coin = coin;
    }
    
    public double getCoin(){
        return this.coin;
    }
    
    public void setPoint(int point){
        this.point = point;
    }
    
    public int getPoint(){
        return this.point;
    }
    
    // 判断是否含有附件
    public boolean hasFile(){
        if(fileName.equals("0")){
            return hasFileContent();
        }else{
            return (readFile() && hasFileContent());
        }
    }
    
    // 判断是否含有任何附件内容
    public boolean hasFileContent(){
        return (hasItem || hasCommand || ((GlobalConfig.enVault && coin!=0) || (GlobalConfig.enPlayerPoints && point!=0)));
    }
    
    // 获取附件信息
    public final boolean readFile(){
        if(GlobalConfig.fileSQL){
            return MailBoxAPI.getMailFilesSQL(this);
        }else{
            return MailBoxAPI.getMailFilesLocal(this);
        }
    }
    
    // 保存附件
    public boolean saveFile(){
        if(GlobalConfig.fileSQL){
            return MailBoxAPI.saveMailFilesSQL(this);
        }else{
            return MailBoxAPI.saveMailFilesLocal(this);
        }
    }
    
    @Override
    public BaseFileMail setType(String type){
        return MailBoxAPI.createBaseFileMail(type, getId(),getSender(), null, null, getTopic(),getContent(),getDate(), null, 0, null, false, null, fileName, itemList, commandList, commandDescription, coin, point);
    }
    
    @Override
    public final BaseFileMail addFile(){
        return this;
    }
    
    public BaseMail removeFile(){
        return new BaseMail(getType(),getId(),getSender(),getTopic(),getContent(),getDate());
    }
    
    @Override
    public String toString(){
        StringBuilder str = new StringBuilder(super.toString());
        if(hasItem && !itemList.isEmpty()){
            str.append("§r-含物品");
            str.append(itemList.size());
            str.append("个");
        }
        if(hasCommand && !commandList.isEmpty()){
            str.append("§r-含指令");
            str.append(commandList.size());
            str.append("条");
        }
        if(coin!=0){
            str.append("§r-含");
            str.append(coin);
            str.append(Message.moneyVault);
        }
        if(point!=0){
            str.append("§r-含");
            str.append(point);
            str.append(Message.moneyPlayerpoints);
        }
        return str.toString();
    }
    
}
