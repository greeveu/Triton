package com.rexcantor64.triton;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rexcantor64.triton.bridge.BungeeBridgeManager;
import com.rexcantor64.triton.commands.bungee.MainCMD;
import com.rexcantor64.triton.language.Language;
import com.rexcantor64.triton.language.item.LanguageItem;
import com.rexcantor64.triton.language.item.LanguageSign;
import com.rexcantor64.triton.language.item.LanguageText;
import com.rexcantor64.triton.packetinterceptor.BungeeListener;
import com.rexcantor64.triton.packetinterceptor.ProtocolLibListener;
import com.rexcantor64.triton.player.BungeeLanguagePlayer;
import com.rexcantor64.triton.plugin.PluginLoader;
import com.rexcantor64.triton.utils.NMSUtils;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.protocol.DefinedPacket;

import java.io.File;
import java.util.List;

public class BungeeMLP extends MultiLanguagePlugin {

    public BungeeMLP(PluginLoader loader) {
        super.loader = loader;
    }

    @Override
    public void onEnable() {
        instance = this;
        super.onEnable();

        BungeeCord.getInstance().getPluginManager().registerListener(loader.asBungee(), new BungeeBridgeManager());
        BungeeCord.getInstance().registerChannel("MultiLanguagePlugin");

        for (ProxiedPlayer p : BungeeCord.getInstance().getPlayers()) {
            BungeeLanguagePlayer lp = (BungeeLanguagePlayer) getPlayerManager().get(p.getUniqueId());
            setCustomUnsafe(lp);
        }

        BungeeCord.getInstance().getPluginManager().registerCommand(loader.asBungee(), new MainCMD());

        sendConfigToEveryone();
    }

    @Override
    public void reload() {
        super.reload();
        sendConfigToEveryone();
    }

    private void sendConfigToEveryone() {
        try {
            ByteArrayDataOutput languageOut = ByteStreams.newDataOutput();
            // Action 0 (send config)
            languageOut.writeByte(0);
            languageOut.writeUTF(MultiLanguagePlugin.get().getLanguageManager().getMainLanguage().getName());
            List<Language> languageList = MultiLanguagePlugin.get().getLanguageManager().getAllLanguages();
            languageOut.writeShort(languageList.size());
            for (Language language : languageList) {
                languageOut.writeUTF(language.getName());
                languageOut.writeUTF(language.getRawDisplayName());
                languageOut.writeUTF(language.getFlagCode());
                languageOut.writeShort(language.getMinecraftCodes().size());
                for (String code : language.getMinecraftCodes())
                    languageOut.writeUTF(code);
            }

            // Send language files
            for (ServerInfo info : BungeeCord.getInstance().getServers().values()) {
                List<LanguageItem> languageItems = MultiLanguagePlugin.get().getLanguageConfig().getItems();
                int size = 0;
                ByteArrayDataOutput languageItemsOut = ByteStreams.newDataOutput();
                for (LanguageItem item : languageItems) {
                    if (!item.isUniversal() && (!item.isBlacklist() || item.getServers().contains(info.getName())) && (item.isBlacklist() || !item.getServers().contains(info.getName())))
                        continue;
                    switch (item.getType()) {
                        case TEXT:
                            // Send type (0)
                            languageItemsOut.writeByte(0);
                            LanguageText text = (LanguageText) item;
                            languageItemsOut.writeUTF(text.getKey());
                            short langSize2 = 0;
                            ByteArrayDataOutput langOut2 = ByteStreams.newDataOutput();
                            for (Language lang : languageList) {
                                String msg = text.getMessage(lang.getName());
                                if (msg == null) continue;
                                langOut2.writeUTF(lang.getName());
                                langOut2.writeUTF(msg);
                                langSize2++;
                            }
                            languageItemsOut.writeShort(langSize2);
                            languageItemsOut.write(langOut2.toByteArray());
                            break;
                        case SIGN:
                            // Send type (1)
                            languageItemsOut.writeByte(1);
                            LanguageSign sign = (LanguageSign) item;
                            languageItemsOut.writeUTF(sign.getLocation().getWorld());
                            languageItemsOut.writeInt(sign.getLocation().getX());
                            languageItemsOut.writeInt(sign.getLocation().getY());
                            languageItemsOut.writeInt(sign.getLocation().getZ());
                            short langSize = 0;
                            ByteArrayDataOutput langOut = ByteStreams.newDataOutput();
                            for (Language lang : languageList) {
                                String[] lines = sign.getLines(lang.getName());
                                if (lines == null) continue;
                                langOut.writeUTF(lang.getName());
                                langOut.writeUTF(lines[0]);
                                langOut.writeUTF(lines[1]);
                                langOut.writeUTF(lines[2]);
                                langOut.writeUTF(lines[3]);
                                langSize++;
                            }
                            languageItemsOut.writeShort(langSize);
                            languageItemsOut.write(langOut.toByteArray());
                            break;
                    }
                    size++;
                }
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.write(languageOut.toByteArray());
                out.writeInt(size);
                out.write(languageItemsOut.toByteArray());
                info.sendData("MultiLanguagePlugin", out.toByteArray());
            }
        } catch (Exception e) {
            logError("Failed to send config and language items to other servers! Not everything might work as expected! Error: %1", e.getMessage());
        }
    }

    public ProtocolLibListener getProtocolLibListener() {
        return null;
    }

    public File getDataFolder() {
        return loader.asBungee().getDataFolder();
    }

    public void setCustomUnsafe(BungeeLanguagePlayer p) {
        NMSUtils.setPrivateFinalField(p.getParent(), "unsafe", new BungeeListener(p));
    }

    public void setDefaultUnsafe(ProxiedPlayer p) {
        NMSUtils.setPrivateFinalField(p, "unsafe", new Connection.Unsafe() {
            @Override
            public void sendPacket(DefinedPacket p) {
                ((ChannelWrapper) NMSUtils.getDeclaredField(p, "ch")).write(p);
            }
        });
    }

}
