package com.massivecraft.factions;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;

import com.google.gson.reflect.TypeToken;
import com.massivecraft.factions.integration.LWCFeatures;
import com.massivecraft.factions.iface.RelationParticipator;
import com.massivecraft.factions.util.AsciiCompass;
import com.massivecraft.factions.zcore.util.DiscUtil;


public class Board
{
	private static transient File file = new File(P.p.getDataFolder(), "board.json");
	private static transient HashMap<FLocation, String[]> flocationIds = new HashMap<FLocation, String[]>();
	
	//----------------------------------------------//
	// Get and Set
	//----------------------------------------------//
	public static String getIdAt(FLocation flocation)
	{
		if ( ! flocationIds.containsKey(flocation))
		{
			return "0";
		}
		
		return flocationIds.get(flocation)[0];
	}
	
	public static int getClaimIdAt(FLocation flocation){
		if ( ! flocationIds.containsKey(flocation))
		{
			return -1;
		}
		
		try{
			return Integer.parseInt(flocationIds.get(flocation)[1]);
		}catch (Exception e){
			return -1;
		}
		
	}
	
	public static Faction getFactionAt(FLocation flocation)
	{
		return Factions.i.get(getIdAt(flocation));
	}
	public static Faction getFactionAt(Location location)
	{
		return getFactionAt(new FLocation(location));
	}
	public static Faction getFactionAt(Block block)
	{
		return getFactionAt(new FLocation(block));
	}
	
	public static void setIdAt(String[] idClaimOrder, FLocation flocation)
	{
		String id = idClaimOrder[0];
		String claimOrder = idClaimOrder[1];
		if (id == "0")
		{
			removeAt(flocation);
		}
		//TODO  FIX THIS FUCKING SHIT SO IT SAVES AN INCREMENTAL ORDER ID
		flocationIds.put(flocation, new String[]{id,claimOrder});
	}
	
	public static void setFactionAt(Faction faction, FLocation flocation)
	{
		fixClaimOrderIds();
		int nextClaimId = Board.getFactionCoordCount(faction)+1;
		setIdAt(new String[]{faction.getId(), nextClaimId+""}, flocation);
	}
	
	public static void removeAt(FLocation flocation)
	{
		if(Conf.onUnclaimResetLwcLocks && LWCFeatures.getEnabled())
			LWCFeatures.clearAllChests(flocation);

		flocationIds.remove(flocation);
		fixClaimOrderIds();
	}
	
	public static void unclaimAll(String factionId)
	{
		Iterator<Entry<FLocation, String[]>> iter = flocationIds.entrySet().iterator();
		while (iter.hasNext())
		{
			Entry<FLocation, String[]> entry = iter.next();
			if (entry.getValue()[0].equals(factionId))
			{
					if(Conf.onUnclaimResetLwcLocks && LWCFeatures.getEnabled())
						LWCFeatures.clearAllChests(entry.getKey());

					iter.remove();
			}
		}
		fixClaimOrderIds();
	}

	// Is this coord NOT completely surrounded by coords claimed by the same faction?
	// Simpler: Is there any nearby coord with a faction other than the faction here?
	public static boolean isBorderLocation(FLocation flocation)
	{
		Faction faction = getFactionAt(flocation);
		FLocation a = flocation.getRelative(1, 0);
		FLocation b = flocation.getRelative(-1, 0);
		FLocation c = flocation.getRelative(0, 1);
		FLocation d = flocation.getRelative(0, -1);
		return faction != getFactionAt(a) || faction != getFactionAt(b) || faction != getFactionAt(c) || faction != getFactionAt(d);
	}

	// Is this coord connected to any coord claimed by the specified faction?
	public static boolean isConnectedLocation(FLocation flocation, Faction faction)
	{
		FLocation a = flocation.getRelative(1, 0);
		FLocation b = flocation.getRelative(-1, 0);
		FLocation c = flocation.getRelative(0, 1);
		FLocation d = flocation.getRelative(0, -1);
		return faction == getFactionAt(a) || faction == getFactionAt(b) || faction == getFactionAt(c) || faction == getFactionAt(d);
	}
	
	//----------------------------------------------//
	// ID Cleaner. Reorder and renumber claimOrderId's
	//----------------------------------------------//
	
	public static void fixClaimOrderIds(){
		for (Faction faction : Factions.i.get())
		{
			String factionId = faction.getId();
			int coordCount = getFactionCoordCount(factionId);
			Iterator<Entry<FLocation, String[]>> iter = flocationIds.entrySet().iterator();
			TreeMap<String, FLocation> sortMap = new TreeMap<String, FLocation>();
			//Gather the current claims in their current natural order
			while (iter.hasNext()) {
				Entry<FLocation, String[]> entry = iter.next();
				if (entry.getValue()[0].equalsIgnoreCase(factionId))
				{
					sortMap.put(entry.getValue()[1], entry.getKey());
				}
			}
			//Renumber them starting at 1 in their natural order
			int newOrder = 1;
			Iterator<Entry<String, FLocation>> anotherIter = sortMap.entrySet().iterator();
			while(anotherIter.hasNext()){
				Entry<String, FLocation> anotherEntry = anotherIter.next();
				FLocation locationToUpdate = anotherEntry.getValue();
				String[] idClaim = new String[]{factionId,""+newOrder};
				setIdAt(idClaim, locationToUpdate);
				newOrder++;
			}
		}
		P.p.log("Board Claim Order Id's cleaned");
	}
	
	//----------------------------------------------//
	// Cleaner. Remove orphaned foreign keys
	//----------------------------------------------//
	
	public static void clean()
	{
		Iterator<Entry<FLocation, String[]>> iter = flocationIds.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<FLocation, String[]> entry = iter.next();
			if ( ! Factions.i.exists(entry.getValue()[0]))
			{
				if(Conf.onUnclaimResetLwcLocks && LWCFeatures.getEnabled())
					LWCFeatures.clearAllChests(entry.getKey());

				P.p.log("Board cleaner removed "+entry.getValue()+" from "+entry.getKey());
				iter.remove();
			}
		}
	}	
	
	
	
	//----------------------------------------------//
	// Coord count
	//----------------------------------------------//
	
	public static int getFactionCoordCount(String factionId)
	{
		int ret = 0;
		for (String[] thatFactionId : flocationIds.values())
		{
			if(thatFactionId[0].equals(factionId))
			{
				ret += 1;
			}
		}
		return ret;
	}
	
	public static int getFactionCoordCount(Faction faction)
	{
		return getFactionCoordCount(faction.getId());
	}
	
	public static int getFactionCoordCountInWorld(Faction faction, String worldName)
	{
		String factionId = faction.getId();
		int ret = 0;
		Iterator<Entry<FLocation, String[]>> iter = flocationIds.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<FLocation, String[]> entry = iter.next();
			if (entry.getValue()[0].equals(factionId) && entry.getKey().getWorldName().equals(worldName))
			{
				ret += 1;
			}
		}
		return ret;
	}
	
	//----------------------------------------------//
	// Map generation
	//----------------------------------------------//
	
	/**
	 * The map is relative to a coord and a faction
	 * north is in the direction of decreasing x
	 * east is in the direction of decreasing z
	 */
	public static ArrayList<String> getMap(RelationParticipator observer, FLocation flocation, double inDegrees)
	{
		ArrayList<String> ret = new ArrayList<String>();
		Faction factionLoc = getFactionAt(flocation);
		ret.add(P.p.txt.titleize("("+flocation.getCoordString()+") "+factionLoc.getTag(observer)));
		
		int halfWidth = Conf.mapWidth / 2;
		int halfHeight = Conf.mapHeight / 2;
		FLocation topLeft = flocation.getRelative(-halfWidth, -halfHeight);
		int width = halfWidth * 2 + 1;
		int height = halfHeight * 2 + 1;
		
		//Make room for the list of tags
		height--;
		
		
		Map<Faction, Character> fList = new HashMap<Faction, Character>();
		int chrIdx = 0;
		
		// For each row
		for (int dz = 0; dz < height; dz++)
		{
			// Draw and add that row
			String row = "";
			for (int dx = 0; dx < width; dx++)
			{
				if(dx == halfWidth && dz == halfHeight)
				{
					row += ChatColor.AQUA+"+";
					continue;
				}
			
				FLocation flocationHere = topLeft.getRelative(dx, dz);
				Faction factionHere = getFactionAt(flocationHere);
				if (factionHere.isNone())
				{
					row += ChatColor.GRAY+"-";
				}
				else
				{
					if (!fList.containsKey(factionHere))
						fList.put(factionHere, Conf.mapKeyChrs[chrIdx++]);
					char fchar = fList.get(factionHere);
					row += factionHere.getColorTo(observer) + "" + fchar;
				}
			}
			ret.add(row);
		}
		
		// Get the compass
		ArrayList<String> asciiCompass = AsciiCompass.getAsciiCompass(inDegrees, ChatColor.RED, P.p.txt.parse("<a>"));

		// Add the compass
		ret.set(1, asciiCompass.get(0)+ret.get(1).substring(3*3));
		ret.set(2, asciiCompass.get(1)+ret.get(2).substring(3*3));
		ret.set(3, asciiCompass.get(2)+ret.get(3).substring(3*3));
			
		String fRow = "";
		for(Faction keyfaction : fList.keySet())
		{
			fRow += ""+keyfaction.getColorTo(observer) + fList.get(keyfaction) + ": " + keyfaction.getTag() + " ";
		}
		fRow = fRow.trim();
		ret.add(fRow);
		
		return ret;
	}
	
	
	// -------------------------------------------- //
	// Persistance
	// -------------------------------------------- //
	
	public static Map<String,Map<String,String>> dumpAsSaveFormat()
	{
		Map<String,Map<String,String>> worldCoordIds = new HashMap<String,Map<String,String>>(); 
		
		String worldName, coords;
		String id;
		String claimId;
		
		for (Entry<FLocation, String[]> entry : flocationIds.entrySet())
		{
			worldName = entry.getKey().getWorldName();
			coords = entry.getKey().getCoordString();
			id = entry.getValue()[0];
			claimId = entry.getValue()[1];
			if ( ! worldCoordIds.containsKey(worldName))
			{
				worldCoordIds.put(worldName, new TreeMap<String,String>());
			}
			
			worldCoordIds.get(worldName).put(coords, id+","+claimId);
		}
		
		return worldCoordIds;
	}
	
	public static void loadFromSaveFormat(Map<String,Map<String,String>> worldCoordIds)
	{
		flocationIds.clear();
		
		String worldName;
		String[] coords;
		int x, z;
		String factionId;
		String claimOrderId;
		
		for (Entry<String,Map<String,String>> entry : worldCoordIds.entrySet())
		{
			worldName = entry.getKey();
			for (Entry<String,String> entry2 : entry.getValue().entrySet())
			{
				coords = entry2.getKey().trim().split("[,\\s]+");
				x = Integer.parseInt(coords[0]);
				z = Integer.parseInt(coords[1]);
				String[] entryStr = entry2.getValue().split("[,\\s]+");
				factionId = entryStr[0];
				claimOrderId = entryStr[1];
				flocationIds.put(new FLocation(worldName, x, z), entryStr);
			}
		}
	}
	
	public static boolean save()
	{
		//Factions.log("Saving board to disk");
		fixClaimOrderIds();
		
		try
		{
			DiscUtil.write(file, P.p.gson.toJson(dumpAsSaveFormat()));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			P.p.log("Failed to save the board to disk.");
			return false;
		}
		
		return true;
	}
	
	public static boolean load()
	{
		P.p.log("Loading board from disk");
		
		if ( ! file.exists())
		{
			P.p.log("No board to load from disk. Creating new file.");
			save();
			return true;
		}
		
		try
		{
			Type type = new TypeToken<Map<String,Map<String,String>>>(){}.getType();
			Map<String,Map<String,String>> worldCoordIds = P.p.gson.fromJson(DiscUtil.read(file), type);
			loadFromSaveFormat(worldCoordIds);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			P.p.log("Failed to load the board from disk.");
			return false;
		}
			
		return true;
	}
}



















