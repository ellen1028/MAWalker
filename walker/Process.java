package walker;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.Network;

import org.w3c.dom.Document;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

import walker.Process;
import walker.Info.TimeoutEntry;
import action.ActionRegistry.Action;
import action.AddArea;
import action.Explore;
import action.GetFairyList;
import action.GetFairyReward;
import action.GetFloorInfo;
import action.GetRank;
import action.GetRewardBox;
import action.GotoFloor;
import action.GuildBattle;
import action.GuildTop;
import action.Login;
import action.LvUp;
import action.PFBGood;
import action.PartyRank;
import action.PrivateFairyBattle;
import action.RecvPFBGood;
import action.SellCard;
import action.Use;

public class Process {
	public static Info info;
	public static Network network;

	public Process() {
		info = new Info();
		network = new Network();
	}

	public void auto() throws Exception {
		try {
			if (ErrorData.currentErrorType != ErrorData.ErrorType.none) {
				rescue();
			} else {
				long start = System.currentTimeMillis();
				execute(Think.doIt(getPossibleAction()));
				long delta = System.currentTimeMillis() - start;

				if (delta < 5000) {
					Thread.sleep(5000 - delta);
				}

				if (info.events.empty() && info.NoFairy) {
					Thread.sleep(30000);
				}
			}
		} catch (Exception ex) {
			throw ex;
		}
	}

	private void rescue() {
		Go.log(ErrorData.currentErrorType.toString());
		switch (ErrorData.currentDataType) {
		case bytes:
			if (ErrorData.bytes != null) {
				Go.log(new String(ErrorData.bytes));
			} else {
				Go.log("Set type to byte, but no message");
			}
			break;
		case text:
			Go.log(ErrorData.text);
			break;
		default:
			break;
		}
		ErrorData.clear();
	}

	private List<Action> getPossibleAction() {
		ArrayList<Action> result = new ArrayList<Action>();
		if (info.events.size() != 0) {
			switch (info.events.pop()) {
			case notLoggedIn:
			case cookieOutOfDate:
				result.add(Action.LOGIN);
				break;
			case fairyTransform:
				Go.log("Rare Fairy Appear");
			case privateFairyAppear:
			case fairyCanBattle:
				result.add(Action.PRIVATE_FAIRY_BATTLE);
				break;
			case fairyReward:
				if (info.ticket > 10) {
					result.add(Action.GUILD_TOP);
				} else if (info.ticket < 0) {
					Go.log("Keep reward");
				} else {
					try {
						if (GetFairyReward.run()) {
							Go.log(ErrorData.text);
							ErrorData.clear();

							if (Info.receiveBox == true) {
								GetRewardBox.list();
								if (Process.info.boxList.size() >= 650)
									GetRewardBox.get();
							}
						}
					} catch (Exception ex) {
					}
				}
				break;
			case innerMapJump:
				Go.log("Map Status Changed!");
				result.add(Action.GET_FLOOR_INFO);
				break;
			case needFloorInfo:
				result.add(Action.GET_FLOOR_INFO);
				break;
			case areaComplete:
				result.add(Action.ADD_AREA);
				break;
			case cardFull:
				result.add(Action.SELL_CARD);
				break;
			case fairyAppear:
				result.add(Action.GET_FAIRY_LIST);
				break;
			case getFairyReward:
				break;
			case guildBattle:
				if (info.bc == info.bcMax && info.ticket < 15)
					break;
				if (info.gfairy.Spp.equals("使用BC3％回復")) {
					result.add(Action.GUILD_BATTLE);
				} else if (info.ticket >= 15) {
					result.add(Action.GUILD_BATTLE);
				} else if (!Process.info.gfairy.UserId
						.equals(Process.info.username)) {
					result.add(Action.GUILD_BATTLE);
				}
				break;
			case guildTopRetry:
			case guildTop:
			case ticketFull:
				result.add(Action.GUILD_TOP);
				break;
			case needAPBCInfo:
				result.add(Action.GOTO_FLOOR);
				break;
			case levelUp:
				if (Info.AutoAddp == 0) {
					// 输出次数太多，屏蔽掉
					// Go.log("自动加点已关闭");
				} else {
					result.add(Action.LV_UP);
				}
			case fairyBattleEnd:
			case fairyBattleLose:
			case fairyBattleWin:
				break;
			case PFBGood:
				result.add(Action.PFB_GOOD);
				break;
			case recvPFBGood:
				result.add(Action.RECV_PFB_GOOD);
				break;
			case gotoFloor:
				result.add(Action.GOTO_FLOOR);
				break;
			}

			if (!result.isEmpty()) {
				return result;
			}
		}

		ArrayList<TimeoutEntry> te = info.CheckTimeout();
		for (TimeoutEntry e : te) {
			switch (e) {
			case apbc:
				Process.info.events.push(Info.EventType.needAPBCInfo);
				break;
			case fairy:
				Process.info.events.push(Info.EventType.fairyAppear);
				break;
			case login:
				Process.info.events.push(Info.EventType.cookieOutOfDate);
				break;
			case map:
				Process.info.events.push(Info.EventType.needFloorInfo);
				break;
			case ticket:
				if (info.ticket > 0)
					Process.info.events.push(Info.EventType.cardFull);
				break;
			case reward:
			default:
				break;
			}
		}

		if (!Process.info.OwnFairyKilled && Info.GoStop
				&& (info.ap < info.apMax || info.lv < 30)) {
			Process.info.count1++;
			if (Process.info.count1 < 5) {
				result.add(Action.GET_FAIRY_LIST);
				Go.log("sleeping......");
			} else {
				Process.info.count1 = 0;
			}
			try {
				Thread.sleep(15000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		} else {
			result.add(Action.EXPLORE);
		}

		result.add(Action.SELL_CARD);
		result.add(Action.USE);

		if (Info.FairyBattleFirst) {
			result.add(Action.GET_FAIRY_LIST);
		}

		return result;
	}

	private void execute(Action action) throws Exception {
		switch (action) {
		case LOGIN:
			try {
				if (Login.run()) {
					Go.log(String
							.format("玩家: %s, AP: %d/%d, BC: %d/%d, Card: %d/%d, ticket: %d, money: %d",
									info.username, info.ap, info.apMax,
									info.bc, info.bcMax, info.cardList.size(),
									info.cardMax, info.ticket, info.money));
					info.events.push(Info.EventType.fairyAppear);
					info.events.push(Info.EventType.needFloorInfo);

					int gatherRank = GetRank.gatherrank(
							Integer.parseInt(info.userId), 5);
					int guildRank = GetRank.gatherrank(
							Integer.parseInt(info.userId), 4);

					Go.log(String.format("收集排名: %d, 骑士团个人排名: %d", gatherRank,
							guildRank));

					if (Info.partyrank)
						PartyRank.run();

					if (Info.receiveBox == true)
						Login.listRewardbox();

				} else {
					info.events.push(Info.EventType.notLoggedIn);
				}
			} catch (Exception ex) {
				info.events.push(Info.EventType.notLoggedIn);
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none) {
					throw ex;
				}
			}
			break;
		case GET_FLOOR_INFO:
			try {
				if (GetFloorInfo.run()) {
					// if (Process.info.AllClear | Info.MinAPOnly)
					// Process.info.front = Process.info.floor.firstEntry()
					// .getValue();
					Go.log(String.format(
							"Area:%s Floor:%s层 消耗=%d",
							info.area.get(Integer.parseInt(info.front.areaId)).areaName,
							info.front.floorId, info.front.cost));
				}

			} catch (Exception ex) {
				if (ex.getMessage() != null && ex.getMessage().equals("302")) {
					info.events.push(Info.EventType.innerMapJump);
					ErrorData.clear();
				} else {
					if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
						throw ex;
				}
			}
			break;
		case ADD_AREA:
			try {
				if (AddArea.run()) {
					Go.log(String.format(
							"Area:%s Floor:%s层 消耗=%d",
							info.area.get(Integer.parseInt(info.front.areaId)).areaName,
							info.front.floorId, info.front.cost));
				}

			} catch (Exception ex) {
				if (ex.getMessage().equals("302")) {
					info.events.push(Info.EventType.innerMapJump);
					ErrorData.clear();
				} else {
					if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
						throw ex;
				}
			}
			break;
		case GET_FAIRY_LIST:
			try {
				if (GetFairyList.run()) {
					if (!info.events.empty()
							&& info.events.peek() == Info.EventType.fairyCanBattle) {
						Go.log("Other's fairy found!");
					} else {
						Go.log("No fairy found.");
					}
				} else {
					if (Info.FairyBattleFirst)
						info.events.push(Info.EventType.fairyAppear);
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.ConnectionError) {
					info.events.push(Info.EventType.fairyAppear); // 再次检测
					Go.log("Retry@GetFairyList");
					ErrorData.clear();
				} else if (ErrorData.currentErrorType == ErrorData.ErrorType.none) {
					throw ex;
				}
			}

			break;
		case GOTO_FLOOR:
			try {
				if (GotoFloor.run()) {
					Go.log(String.format(
							"Goto: AP: %d/%d, BC: %d/%d, Front:%s > %s层",
							info.ap,
							info.apMax,
							info.bc,
							info.bcMax,
							info.area.get(Integer.parseInt(info.front.areaId)).areaName,
							info.front.floorId));
				} else {

				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;

			}

			break;
		case PRIVATE_FAIRY_BATTLE:
			try {
				if (PrivateFairyBattle.run()) {
					String result = "";
					if (!info.events.empty()) {
						switch (info.events.peek()) {
						case fairyBattleEnd:
							result = "Late";
							info.events.pop();
							break;
						case fairyBattleLose:
							info.count2++;
							result = "Lose";
							info.events.pop();
							break;
						case fairyBattleWin:
							info.count3++;
							result = "Win";
							info.events.pop();
							break;
						default:
							break;
						}
					}
					String str1 = String
							.format("妖精:%s(%s), Lv:%3s, HP:%6d/%6d",
									info.fairy.FairyName,
									info.FairySelectUserList
											.containsKey(info.fairy.UserId) ? info.FairySelectUserList
											.get(info.fairy.UserId).userName
											: "NA", info.fairy.FairyLevel,
									info.fairy.fairyCurrHp,
									info.fairy.fairyMaxHp);
					String str2 = String.format(
							"状态:bc:%3d/%3d, ap:%3d/%3d, T:%2d, %s", info.bc,
							info.bcMax, info.ap, info.apMax, info.ticket,
							result);
					Go.log(str1);
					Go.log(str2);
					if (info.gather != -1) {
						String str3 = String.format("收集:%6d, 舔:%3d, 尾:%3d",
								info.gather, info.count2, info.count3);
						Go.log(str3);
					}
				} else {

				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			} finally {
				// reset
				Process.info.fairy.No = "";
			}

			break;
		case EXPLORE:
			try {
				if (Explore.run()) {
					Go.log(String
							.format("Explore[%s>%s层>%s]: AP: %d, Gold+%s=%d, Exp+%s, Progress:%s, Result: %s.",
									info.area.get(Integer
											.parseInt(info.front.areaId)).areaName,
									info.front.floorId, info.front.cost,
									info.ap, info.ExploreGold, info.money,
									info.ExploreExp, info.ExploreProgress,
									info.ExploreResult));
				} else {

				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case GUILD_BATTLE:
			try {
				if (GuildBattle.run()) {
					String result = "";
					if (!info.events.empty()) {
						switch (info.events.peek()) {
						case guildTopRetry:
							result = "Late";
							break;
						case fairyBattleLose:
							result = "Lose";
							info.events.pop();
							break;
						case fairyBattleWin:
							result = "Win";
							info.events.pop();
							break;
						default:
							break;
						}
					}
					String str1 = String.format("外敌:%s, Lv:%3s, HP:%8d/%8d",
							info.gfairy.FairyName, info.gfairy.FairyLevel,
							info.gfairy.fairyCurrHp, info.gfairy.fairyMaxHp);
					String str2 = String.format(
							"状态:bc:%3d/%3d, ap:%3d/%3d, Buff:%10s, %4s",
							info.bc, info.bcMax, info.ap, info.apMax,
							info.gfairy.Spp, result);
					String str3 = String.format(
							"贡献:%5s+%5s=%5s(%3s)week:%9s, T:%2d, exp:%5d",
							info.gfairy.battle_contribution,
							info.gfairy.hp_contribution,
							info.gfairy.contribution,
							info.gfairy.attack_compensation, info.week,
							info.ticket, info.exp);
					Thread.sleep(5000);
					Go.log(str1);
					Go.log(str2);
					Go.log(str3);
				} else {

				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case GUILD_TOP:
			try {
				GuildTop.run();
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case GET_FAIRY_REWARD:
			try {
				if (GetFairyReward.run()) {
					Go.log(ErrorData.text);
					ErrorData.clear();

					if (Info.receiveBox == true) {
						GetRewardBox.list();
						if (Process.info.boxList.size() >= 650)
							GetRewardBox.get();

					}
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case LV_UP:
			try {
				if (LvUp.run()) {
					// Go.log(String.format("Level UP! AP:%d BC:%d",
					// Process.info.apMax, Process.info.bcMax));
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;

		case SELL_CARD:
			try {
				if (SellCard.run()) {
					Go.log(ErrorData.text);
					ErrorData.clear();
				} else {
					Go.log("Something wrong");
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case USE:
			try {
				if (Use.run()) {
					Go.log(ErrorData.text);
					ErrorData.clear();
					Go.log(String
							.format("Bottles: FA:%d, HA:%d, HA(T):%d, FB:%d, HB:%d, HB(T):%d",
									info.fullAp, info.halfAp, info.halfApToday,
									info.fullBc, info.halfBc, info.halfBcToday));
				} else {
					Go.log("Sth Wrong @USE");
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case PFB_GOOD:
			try {
				if (PFBGood.run()) {
					Go.log(ErrorData.text);
					ErrorData.clear();
				} else {
					Go.log("Something wrong");
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;

			}
			break;
		case RECV_PFB_GOOD:
			try {
				if (RecvPFBGood.run()) {
					Go.log(ErrorData.text);
					ErrorData.clear();
				} else {
					Go.log("Something wrong");
				}
			} catch (Exception ex) {
				if (ErrorData.currentErrorType == ErrorData.ErrorType.none)
					throw ex;
			}
			break;
		case NOTHING:
			Thread.sleep(30000); // 无事可做休息30秒
			break;
		default:
			break;

		}
	}

	public static Document ParseXMLBytes1(byte[] in) throws Exception {
		ByteArrayInputStream bais = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			bais = new ByteArrayInputStream(in);
			Document document = builder.parse(bais);
			return document;
		} catch (Exception e) {
			throw e;
		}
	}

	public static Document ParseXMLBytes(byte[] in) throws Exception {
		ByteArrayInputStream bais = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			bais = new ByteArrayInputStream(in);
			Document document = builder.parse(bais);
			if (Info.debug)
				doc2FormatString(document); // 输出xml
			return document;
		} catch (Exception e) {
			throw e;
		}
	}

	public static void doc2FormatString(Document doc) {
		String docString = "";
		if (doc != null) {
			StringWriter stringWriter = new StringWriter();
			try {
				OutputFormat format = new OutputFormat(doc, "UTF-8", true);
				// format.setIndenting(true);//设置是否缩进，默认为true
				// format.setIndent(4);//设置缩进字符数
				// format.setPreserveSpace(false);//设置是否保持原来的格式,默认为 false
				// format.setLineWidth(500);//设置行宽度
				XMLSerializer serializer = new XMLSerializer(stringWriter,
						format);
				serializer.asDOMSerializer();
				serializer.serialize(doc);
				docString = stringWriter.toString();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (stringWriter != null) {
					try {
						stringWriter.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		File f = new File("xml/");
		// 创建文件夹
		if (!f.exists()) {
			f.mkdirs();
		}

		// System.out.println(docString);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		File fp = new File(String.format("xml/%s.xml", df.format(new Date())));
		PrintWriter pfp;
		try {
			pfp = new PrintWriter(fp);
			pfp.print(docString);
			pfp.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		// return docString;
	}
}
