package com.sedmelluq.discord.lavaplayer.demo.jda;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;

public class Main extends ListenerAdapter {
	public static String tag ="!";
	
	
  public static void main(String[] args) throws Exception {
    JDA jda = new JDABuilder(AccountType.BOT)
        .setToken("NDk2NzAwODU4MTYwMTE5ODM4.DpVgqw.K7tePLOkhnuN4AhSiYt6iMOegxA")
        .buildBlocking();

    jda.addEventListener(new Main());
  }

  private final AudioPlayerManager playerManager;
  private final Map<Long, GuildMusicManager> musicManagers;

  private Main() {
    this.musicManagers = new HashMap<>();

    this.playerManager = new DefaultAudioPlayerManager();
    AudioSourceManagers.registerRemoteSources(playerManager);
    AudioSourceManagers.registerLocalSource(playerManager);
  }

  private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
    long guildId = Long.parseLong(guild.getId());
    GuildMusicManager musicManager = musicManagers.get(guildId);

    if (musicManager == null) {
      musicManager = new GuildMusicManager(playerManager);
      musicManagers.put(guildId, musicManager);
    }

    guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

    return musicManager;
  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
	  User user = event.getAuthor();
	  TextChannel channel = event.getTextChannel();
	  
	  Guild guild = event.getGuild();
	  VoiceChannel connectedChannel = event.getMember().getVoiceState().getChannel();
	  GuildMusicManager musicManager = getGuildAudioPlayer(guild);
	  AudioTrack stopedTrack = null;
    String[] command = event.getMessage().getContentRaw().split(" ", 2);
    
    
    if (guild != null) {
      if (Commands.play.equals(command[0]) && command.length == 2) {
        loadAndPlay(channel, command[1],guild,connectedChannel);
      } else if (Commands.skip.equals(command[0])) {
        skipTrack(event.getTextChannel());
      }else if (Commands.leave.equals(command[0])) {
    	  if(connectedChannel!=null) {
    		  guild.getAudioManager().closeAudioConnection();
    	  }else{
    		  channel.sendMessage("Я не подключён к Voice channel!").queue();
    	  }
      }else if (Commands.queue.equals(command[0])) {
    	  BlockingQueue<AudioTrack> trackList = musicManager.scheduler.getQueue();
    	  if(!trackList.isEmpty()) {
    		  int counter = 1;
    		  for(AudioTrack track : trackList) {
    			  channel.sendMessage(counter+") "+ track.getInfo().title).queue();
    		  }
    	  }else {
    		  channel.sendMessage("Play list пуст!").queue();
    	  }
      }else if (Commands.pause.equals(command[0])) {
    	   musicManager.player.setPaused(true);
      }else if (Commands.resume.equals(command[0])) {
    	  if(musicManager.player.isPaused()) {
    	  musicManager.player.setPaused(false);
    	  }
      }else if (Commands.stop.equals(command[0])) {    	   	  
        	  musicManager.player.stopTrack(); 	  
      }else if (Commands.curr_song.equals(command[0])) {  
    	  System.out.println(user.getName()+" сейчас играет - "+ musicManager.player.getPlayingTrack().getInfo().title);
    	  channel.sendMessage(user.getName()+" сейчас играет - "+ musicManager.player.getPlayingTrack().getInfo().title).queue(); 	  
       }
    }

    super.onMessageReceived(event);
  }

  private void loadAndPlay(TextChannel channel, final String trackUrl,Guild guild,VoiceChannel connectedChannel) {
    GuildMusicManager musicManager = getGuildAudioPlayer(guild);

    playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
      @Override
      public void trackLoaded(AudioTrack track) {
        channel.sendMessage("Добавляю в очередь " + track.getInfo().title).queue();

        play(guild, musicManager, track,connectedChannel,channel);
      }

      @Override
      public void playlistLoaded(AudioPlaylist playlist) {
        AudioTrack firstTrack = playlist.getSelectedTrack();

        if (firstTrack == null) {
          firstTrack = playlist.getTracks().get(0);
        }

        channel.sendMessage("Добавляю в очередь " + firstTrack.getInfo().title + " (Первый трек в плей листе " + playlist.getName() + ")").queue();

        play(guild, musicManager, firstTrack,connectedChannel,channel);
      }

      @Override
      public void noMatches() {
        channel.sendMessage("Ничего не нашёл =( " + trackUrl).queue();
      }

      @Override
      public void loadFailed(FriendlyException exception) {
        channel.sendMessage("Немогу воспроизвести: " + exception.getMessage()).queue();
      }
    });
  }

  private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track,VoiceChannel voiceChannel,TextChannel textch) {
    connectToFirstVoiceChannel(guild.getAudioManager(),voiceChannel,textch);
    
    musicManager.scheduler.queue(track);
  }
 
  private void skipTrack(TextChannel channel) {
    GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
    musicManager.scheduler.nextTrack();

    channel.sendMessage("Играем следующий трек!").queue();
  }

  private static void connectToFirstVoiceChannel(AudioManager audioManager,VoiceChannel voiceChannel,TextChannel textch) {
    if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
    	
    	
    	if(voiceChannel == null) {
            // Don't forget to .queue()!
    		textch.sendMessage("Пожалуйста присоеденитесь к voice channel!").queue();
    	}else {
    		audioManager.openAudioConnection(voiceChannel);
    	}
      
    }
  }
}
