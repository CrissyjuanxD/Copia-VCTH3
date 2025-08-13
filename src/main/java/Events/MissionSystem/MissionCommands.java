package Events.MissionSystem;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MissionCommands implements CommandExecutor, TabCompleter {
    private final MissionHandler missionHandler;
    private final MissionGUI missionGUI;

    public MissionCommands(MissionHandler missionHandler, MissionGUI missionGUI) {
        this.missionHandler = missionHandler;
        this.missionGUI = missionGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("activarmision")) {
            if (!sender.hasPermission("viciont_hardcore3.missions.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Uso: /activarmision <número>");
                return true;
            }

            try {
                int missionNumber = Integer.parseInt(args[0]);
                missionHandler.activateMission(sender, missionNumber);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "El número de misión debe ser válido.");
            }
            return true;
        }

        if (label.equalsIgnoreCase("desactivarmision")) {
            if (!sender.hasPermission("viciont_hardcore3.missions.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Uso: /desactivarmision <número>");
                return true;
            }

            try {
                int missionNumber = Integer.parseInt(args[0]);
                missionHandler.deactivateMission(sender, missionNumber);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "El número de misión debe ser válido.");
            }
            return true;
        }

        if (label.equalsIgnoreCase("misiones")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
                return true;
            }

            Player player = (Player) sender;
            missionGUI.openMissionGUI(player);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if ((command.getName().equalsIgnoreCase("activarmision") || 
             command.getName().equalsIgnoreCase("desactivarmision")) && args.length == 1) {
            
            // Sugerir números de misión del 1 al 27
            for (int i = 1; i <= 27; i++) {
                completions.add(String.valueOf(i));
            }
        }

        return completions;
    }
}