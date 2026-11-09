# MeshCentral 1.3.1: server events protocol evidence

This is a protocol audit, not an implemented or validated Android feature. The existing local Activity journal is not a server-history query. No app sources are changed by this audit.

Evidence is extracted from the exact npm package without running package lifecycle scripts. Review request fields, response correlation, ordering, limits, permission checks and live-event behavior before implementing the native client.

Line-numbered Markdown excerpts omit trailing whitespace. Source hashes cover the original unmodified bytes.

## package/meshuser.js

Lines 986-1226:
```javascript
986:                             });
987:                         } else if (command.fileop == 'set') {
988:                             // Set a short file transfered on the web socket
989:                             if (common.validateString(command.file, 1, 4096) == false) return;
990:                             if (typeof command.data != 'string') return;
991:                             const scpath = meshPathToRealPath(command.path, user); // This will also check access rights
992:                             if ((scpath == null) || (command.file !== parent.path.basename(command.file))) break;
993:                             const filePath = parent.path.join(scpath, command.file);
994:                             var data = null;
995:                             try { data = Buffer.from(command.data, 'base64'); } catch (ex) { return; }
996:                             fs.writeFile(filePath, data, function (err) { if (err == null) { parent.parent.DispatchEvent([user._id], obj, 'updatefiles'); } });
997:                         }
998:                         if (sendUpdate == true) { parent.parent.DispatchEvent([user._id], obj, 'updatefiles'); } // Fire an event causing this user to update this files
999:                     }
1000:                     break;
1001:                 }
1002:             case 'software': {
1003:                 if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'software', responseid: command.responseid, result: 'Denied' })); } catch (ex) { } }
1004:
1005:                 parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
1006:                     if (node == null) return;
1007:                     var mesh = parent.meshes[node.meshid];
1008:                     if ((node != null) && (mesh != null) && (rights === MESHRIGHT_ADMIN) || ((rights & MESHRIGHT_NOSOFTWARE) === 0)) {
1009:                         var agent = parent.wsagents[command.nodeid];
1010:                         if (agent != null) {
1011:                             routeCommandToNode(command, requiredRights, requiredNonRights, func, routingOptions);
1012:                         } else {
1013:                             if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'software', responseid: command.responseid, result: 'Agent offline' })); } catch (ex) { } }
1014:                         }
1015:                     } else {
1016:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'software', responseid: command.responseid, result: 'Denied' })); } catch (ex) { } }
1017:                     }
1018:                 });
1019:                 break;
1020:             }
1021:             case 'msg':
1022:                 {
1023:                     // Check the nodeid
1024:                     if (common.validateString(command.nodeid, 1, 1024) == false) {
1025:                         if (command.responseid != null) { try { ws.send(JSON.stringify({ action: 'msg', result: 'Unable to route', tag: command.tag, responseid: command.responseid })); } catch (ex) { } }
1026:                         return;
1027:                     }
1028:
1029:                     // Rights check
1030:                     var requiredRights = null, requiredNonRights = null, routingOptions = null;
1031:
1032:                     // Complete the nodeid if needed
1033:                     if (command.nodeid.indexOf('/') == -1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
1034:
1035:                     // Check if getting / setting clipboard data is allowed
1036:                     if ((command.type == 'getclip') && (domain.clipboardget == false)) { console.log('CG-EXIT'); break; }
1037:                     if ((command.type == 'setclip') && (domain.clipboardset == false)) { console.log('CS-EXIT'); break; }
1038:
1039:                     // Before routing this command, let's do some security checking.
1040:                     // If this is a tunnel request, we need to make sure the NodeID in the URL matches the NodeID in the command.
1041:                     if (command.type == 'tunnel') {
1042:                         if ((typeof command.value != 'string') || (typeof command.nodeid != 'string')) break;
1043:                         var url = null;
1044:                         try { url = new URL(command.value, 'http://localhost'); } catch (ex) { }
1045:                         if (url == null) break; // Bad URL
1046:                         if (url.searchParams.get('nodeid') && (url.searchParams.get('nodeid') != command.nodeid)) break; // Bad NodeID in URL query string
1047:
1048:                         // Check rights
1049:                         if (url.searchParams.get('p') == '1') { requiredNonRights = MESHRIGHT_NOTERMINAL; }
1050:                         else if (url.searchParams.get('p') == '4') { requiredNonRights = MESHRIGHT_NOREGISTRY; }
1051:                         else if ((url.searchParams.get('p') == '5') || (url.searchParams.get('p') == '10')) { requiredNonRights = MESHRIGHT_NOFILES; }
1052:                         else if (url.searchParams.get('p') == '6') { requiredNonRights = MESHRIGHT_NOSOFTWARE; }
1053:
1054:                         // If we are using the desktop multiplexor, remove the VIEWONLY limitation. The multiplexor will take care of enforcing that limitation when needed.
1055:                         if (((parent.parent.config.settings.desktopmultiplex === true) || (domain.desktopmultiplex === true)) && (url.searchParams.get('p') == '2')) { routingOptions = { removeViewOnlyLimitation: true }; }
1056:
1057:                         // Add server TLS cert hash
1058:                         var tlsCertHash = null;
1059:                         if ((parent.parent.args.ignoreagenthashcheck == null) || (parent.parent.args.ignoreagenthashcheck === false)) { // TODO: If ignoreagenthashcheck is an array of IP addresses, not sure how to handle this.
1060:                             tlsCertHash = parent.webCertificateFullHashs[domain.id];
1061:                             if (tlsCertHash != null) { command.servertlshash = Buffer.from(tlsCertHash, 'binary').toString('hex'); }
1062:                         }
1063:
1064:                         // Add user consent messages
1065:                         command.soptions = {};
1066:                         if (typeof domain.consentmessages == 'object') {
1067:                             if (typeof domain.consentmessages.title == 'string') { command.soptions.consentTitle = domain.consentmessages.title; }
1068:                             if (typeof domain.consentmessages.desktop == 'string') { command.soptions.consentMsgDesktop = domain.consentmessages.desktop; }
1069:                             if (typeof domain.consentmessages.terminal == 'string') { command.soptions.consentMsgTerminal = domain.consentmessages.terminal; }
1070:                             if (typeof domain.consentmessages.files == 'string') { command.soptions.consentMsgFiles = domain.consentmessages.files; }
1071:                             if ((typeof domain.consentmessages.consenttimeout == 'number') && (domain.consentmessages.consenttimeout > 0)) { command.soptions.consentTimeout = domain.consentmessages.consenttimeout; }
1072:                             if (domain.consentmessages.autoacceptontimeout === true) { command.soptions.consentAutoAccept = true; }
1073:                             if (domain.consentmessages.autoacceptifnouser === true) { command.soptions.consentAutoAcceptIfNoUser = true; }
1074:                             if (domain.consentmessages.autoacceptifdesktopnouser === true) { command.soptions.consentAutoAcceptIfDesktopNoUser = true; }
1075:                             if (domain.consentmessages.autoacceptifterminalnouser === true) { command.soptions.consentAutoAcceptIfTerminalNoUser = true; }
1076:                             if (domain.consentmessages.autoacceptiffilenouser === true) { command.soptions.consentAutoAcceptIfFileNoUser = true; }
1077:                             if (domain.consentmessages.autoacceptiflocked === true) { command.soptions.consentAutoAcceptIfLocked = true; }
1078:                             if (domain.consentmessages.autoacceptifdesktoplocked === true) { command.soptions.consentAutoAcceptIfDesktopLocked = true; }
1079:                             if (domain.consentmessages.autoacceptifterminallocked === true) { command.soptions.consentAutoAcceptIfTerminalLocked = true; }
1080:                             if (domain.consentmessages.autoacceptiffilelocked === true) { command.soptions.consentAutoAcceptIfFileLocked = true; }
1081:                             if (domain.consentmessages.oldstyle === true) { command.soptions.oldStyle = true; }
1082:                         }
1083:                         if (typeof domain.notificationmessages == 'object') {
1084:                             if (typeof domain.notificationmessages.title == 'string') { command.soptions.notifyTitle = domain.notificationmessages.title; }
1085:                             if (typeof domain.notificationmessages.desktop == 'string') { command.soptions.notifyMsgDesktop = domain.notificationmessages.desktop; }
1086:                             if (typeof domain.notificationmessages.terminal == 'string') { command.soptions.notifyMsgTerminal = domain.notificationmessages.terminal; }
1087:                             if (typeof domain.notificationmessages.files == 'string') { command.soptions.notifyMsgFiles = domain.notificationmessages.files; }
1088:                         }
1089:                         if (typeof domain.terminaluservariable == 'string') { command.soptions.terminalUserVariable = domain.terminaluservariable; }
1090:
1091:                         // Add userid
1092:                         command.userid = user._id;
1093:
1094:                         // Add tunnel pre-message deflate
1095:                         if (typeof parent.parent.config.settings.agentwscompression == 'boolean') { command.perMessageDeflate = parent.parent.config.settings.agentwscompression; }
1096:                     }
1097:
1098:                     // If a response is needed, set a callback function
1099:                     var func = null;
1100:                     if (command.responseid != null) { func = function (r) { try { ws.send(JSON.stringify({ action: 'msg', result: r ? 'OK' : 'Unable to route', tag: command.tag, responseid: command.responseid })); } catch (ex) { } } }
1101:
1102:                     // Route this command to a target node
1103:                     routeCommandToNode(command, requiredRights, requiredNonRights, func, routingOptions);
1104:                     break;
1105:                 }
1106:             case 'events':
1107:                 {
1108:                     // User filtered events
1109:                     if ((command.userid != null) && ((user.siteadmin & SITERIGHT_MANAGEUSERS) != 0)) {
1110:                         if (typeof command.userid != 'string') return;
1111:                         const userSplit = command.userid.split('/');
1112:                         if ((userSplit.length != 3) || (userSplit[1] != domain.id)) return;
1113:
1114:                         // TODO: Add the meshes command.userid has access to (???)
1115:                         var filter = [command.userid];
1116:
1117:                         var actionfilter = null;
1118:                         if (command.filter != null) {
1119:                             if (['agentlog','batchupload','changenode','manual','relaylog','removenode','runcommands'].includes(command.filter)) actionfilter = command.filter;
1120:                         }
1121:
1122:                         if ((command.limit == null) || (typeof command.limit != 'number')) {
1123:                             // Send the list of all events for this session
1124:                             db.GetUserEvents(filter, domain.id, command.userid, actionfilter, function (err, docs) {
1125:                                 if (err != null) return;
1126:                                 try { ws.send(JSON.stringify({ action: 'events', events: docs, userid: command.userid, tag: command.tag })); } catch (ex) { }
1127:                             });
1128:                         } else {
1129:                             // Send the list of most recent events for this session, up to 'limit' count
1130:                             db.GetUserEventsWithLimit(filter, domain.id, command.userid, command.limit, actionfilter, function (err, docs) {
1131:                                 if (err != null) return;
1132:                                 try { ws.send(JSON.stringify({ action: 'events', events: docs, userid: command.userid, tag: command.tag })); } catch (ex) { }
1133:                             });
1134:                         }
1135:                     } else if (command.nodeid != null) { // Device filtered events
1136:                         // Check that the user has access to this nodeid
1137:
1138:                         if (typeof command.nodeid != 'string') return;
1139:                         const nodeSplit = command.nodeid.split('/');
1140:                         if (nodeSplit.length == 1) { command.nodeid = 'node/' + domain.id + '/' + command.nodeid; }
1141:
1142:                         parent.GetNodeWithRights(domain, user, command.nodeid, function (node, rights, visible) {
1143:                             if (node == null) { try { ws.send(JSON.stringify({ action: 'events', events: [], nodeid: command.nodeid, tag: command.tag })); } catch (ex) { } return; }
1144:
1145:                             // Put a limit on the number of returned entries if present
1146:                             var limit = 10000;
1147:                             if (common.validateInt(command.limit, 1, 1000000) == true) { limit = command.limit; }
1148:
1149:                             var filter = null;
1150:                             if (command.filter != null) {
1151:                                 if (['agentlog','batchupload','changenode','manual','relaylog','removenode','runcommands'].includes(command.filter)) filter = command.filter;
1152:                             }
1153:
1154:                             if (((rights & MESHRIGHT_LIMITEVENTS) != 0) && (rights != MESHRIGHT_ADMIN)) {
1155:                                 // Send the list of most recent events for this nodeid that only apply to us, up to 'limit' count
1156:                                 db.GetNodeEventsSelfWithLimit(node._id, domain.id, user._id, limit, filter, function (err, docs) {
1157:                                     if (err != null) return;
1158:                                     try { ws.send(JSON.stringify({ action: 'events', events: docs, nodeid: node._id, tag: command.tag })); } catch (ex) { }
1159:                                 });
1160:                             } else {
1161:                                 // Send the list of most recent events for this nodeid, up to 'limit' count
1162:                                 db.GetNodeEventsWithLimit(node._id, domain.id, limit, filter, function (err, docs) {
1163:                                     if (err != null) return;
1164:                                     try { ws.send(JSON.stringify({ action: 'events', events: docs, nodeid: node._id, tag: command.tag })); } catch (ex) { }
1165:                                 });
1166:                             }
1167:                         });
1168:                     } else {
1169:                         // Create a filter for device groups
1170:                         if ((obj.user == null) || (obj.user.links == null)) return;
1171:
1172:                         // All events
1173:                         var exGroupFilter2 = [], filter = [], filter2 = user.subscriptions;
1174:
1175:                         // Add all meshes for groups this user is part of
1176:                         // TODO (UserGroups)
1177:
1178:                         // Remove MeshID's that we do not have rights to see events for
1179:                         for (var link in obj.user.links) { if (((obj.user.links[link].rights & MESHRIGHT_LIMITEVENTS) != 0) && ((obj.user.links[link].rights != MESHRIGHT_ADMIN))) { exGroupFilter2.push(link); } }
1180:                         for (var i in filter2) { if (exGroupFilter2.indexOf(filter2[i]) == -1) { filter.push(filter2[i]); } }
1181:
1182:                         var actionfilter = null;
1183:                         if (command.filter != null) {
1184:                             if (['agentlog','batchupload','changenode','manual','relaylog','removenode','runcommands'].includes(command.filter)) actionfilter = command.filter;
1185:                         }
1186:
1187:                         if ((command.limit == null) || (typeof command.limit != 'number')) {
1188:                             // Send the list of all events for this session
1189:                             db.GetEvents(filter, domain.id, actionfilter, function (err, docs) {
1190:                                 if (err != null) return;
1191:                                 try { ws.send(JSON.stringify({ action: 'events', events: docs, user: command.user, tag: command.tag })); } catch (ex) { }
1192:                             });
1193:                         } else {
1194:                             // Send the list of most recent events for this session, up to 'limit' count
1195:                             db.GetEventsWithLimit(filter, domain.id, command.limit, actionfilter, function (err, docs) {
1196:                                 if (err != null) return;
1197:                                 try { ws.send(JSON.stringify({ action: 'events', events: docs, user: command.user, tag: command.tag })); } catch (ex) { }
1198:                             });
1199:                         }
1200:                     }
1201:                     break;
1202:                 }
1203:             case 'recordings': {
1204:                 if (((user.siteadmin & SITERIGHT_RECORDINGS) == 0) || (domain.sessionrecording == null)) return; // Check if recordings is enabled and we have rights to do this.
1205:                 var recordingsPath = null;
1206:                 if (domain.sessionrecording.filepath) { recordingsPath = domain.sessionrecording.filepath; } else { recordingsPath = parent.parent.recordpath; }
1207:                 if (recordingsPath == null) return;
1208:                 fs.readdir(recordingsPath, function (err, files) {
1209:                     if (err != null) { try { ws.send(JSON.stringify({ action: 'recordings', error: 1, tag: command.tag })); } catch (ex) { } return; }
1210:                     if ((command.limit == null) || (typeof command.limit != 'number')) {
1211:                         // Send the list of all recordings
1212:                         db.GetEvents(['recording'], domain.id, null, function (err, docs) {
1213:                             if (err != null) { try { ws.send(JSON.stringify({ action: 'recordings', error: 2, tag: command.tag })); } catch (ex) { } return; }
1214:                             for (var i in docs) {
1215:                                 delete docs[i].action; delete docs[i].etype; delete docs[i].msg; // TODO: We could make a more specific query in the DB and never have these.
1216:                                 if (files.indexOf(docs[i].filename) >= 0) { docs[i].present = 1; }
1217:                             }
1218:                             try { ws.send(JSON.stringify({ action: 'recordings', events: docs, tag: command.tag })); } catch (ex) { }
1219:                         });
1220:                     } else {
1221:                         // Send the list of most recent recordings, up to 'limit' count
1222:                         db.GetEventsWithLimit(['recording'], domain.id, command.limit, null, function (err, docs) {
1223:                             if (err != null) { try { ws.send(JSON.stringify({ action: 'recordings', error: 2, tag: command.tag })); } catch (ex) { } return; }
1224:                             for (var i in docs) {
1225:                                 delete docs[i].action; delete docs[i].etype; delete docs[i].msg; // TODO: We could make a more specific query in the DB and never have these.
1226:                                 if (files.indexOf(docs[i].filename) >= 0) { docs[i].present = 1; }
```

## package/views/default.handlebars

Lines 29-99:
```javascript
29:     <script type="text/javascript" src="scripts/amt-redir-ws-0.1.0{{{min}}}.js"></script>
30:     <script type="text/javascript" src="scripts/amt-wsman-ws-0.2.0{{{min}}}.js"></script>
31:     <script type="text/javascript" src="scripts/agent-redir-ws-0.1.1{{{min}}}.js"></script>
32:     <script type="text/javascript" src="scripts/agent-redir-rtc-0.1.0{{{min}}}.js"></script>
33:     <script type="text/javascript" src="scripts/agent-desktop-0.0.2{{{min}}}.js"></script>
34:     <script type="text/javascript" src="scripts/agent-rdp-0.0.1{{{min}}}.js"></script>
35:     <script type="text/javascript" src="scripts/qrcode.min.js"></script>
36:     <script type="text/javascript" src="scripts/xterm{{{min}}}.js"></script>
37:     <script type="text/javascript" src="scripts/xterm-addon-fit{{{min}}}.js"></script>
38:     <script type="text/javascript" src="scripts/xterm-addon-image{{{min}}}.js"></script>
39:     <script type="text/javascript" src="scripts/flatpickr.js"></script>
40:     <script type="text/javascript" src="mstsc/mstsc.js"></script>
41:     <script type="text/javascript" src="mstsc/keyboard.js"></script>
42:     <script type="text/javascript" src="mstsc/rle.js"></script>
43:     <script type="text/javascript" src="mstsc/client.js"></script>
44:     <script type="text/javascript" src="mstsc/canvas.js"></script>
45:     <script keeplink=1 type="text/javascript" src="scripts/u2f-api{{{min}}}.js"></script>
46:     <script keeplink=1 type="text/javascript" src="scripts/charts{{{min}}}.js"></script>
47:     <script keeplink=1 type="text/javascript" src="scripts/moment{{min}}.js"></script>
48:     <script keeplink=1 type="text/javascript" src="scripts/chartjs-adapter-moment{{{min}}}.js"></script>
49:     <script keeplink=1 type="text/javascript" src="scripts/filesaver.min.js"></script>
50:     <script keeplink=1 type="text/javascript" src="scripts/ol{{{min}}}.js"></script>
51:     <script keeplink=1 type="text/javascript" src="scripts/ol3-contextmenu{{{min}}}.js"></script>
52:     <script keeplink=1 type="text/javascript" src="scripts/purify{{{min}}}.js"></script>
53:     <script keeplink=1 type="text/javascript" src="scripts/marked{{{min}}}.js"></script>
54:     {{{customJSTags}}}
55:     <title>{{{title}}}</title>
56: </head>
57: <body id="body" oncontextmenu="handleContextMenu(event)" style="display:none;min-width:495px" onload="if (typeof(startup) !== 'undefined') startup();">
58:     <!-- right click menu -->
59:     <div id="contextMenu" class="contextMenu noselect" style="display:none">
60:         <div id="cxinfo" class="cmtext" onclick="cmaction(1,event)"><b>Information</b></div>
61:         <div id="cxdesktop" class="cmtext" onclick="cmaction(3,event)">Desktop</div>
62:         <div id="cxterminal" class="cmtext" onclick="cmaction(2,event)">Terminal</div>
63:         <div id="cxfiles" class="cmtext" onclick="cmaction(4,event)">Files</div>
64:         <div id="cxevents" class="cmtext" onclick="cmaction(5,event)">Events</div>
65:         <div id="cxdetails" class="cmtext" onclick="cmaction(6,event)">Details</div>
66:         <div id="cxconsole" class="cmtext" onclick="cmaction(7,event)">Console</div>
67:         <div id="cxwebrdp" class="cmtext" onclick="cmaction(11,event)">Web-RDP</div>
68:         <div id="cxwebvnc" class="cmtext" onclick="cmaction(12,event)">Web-VNC</div>
69:         <div id="cxwebssh" class="cmtext" onclick="cmaction(13,event)">Web-SSH</div>
70:         <div id="cxrdp" class="cmtext" onclick="cmaction(14,event)">RDP</div>
71:         <div id="cxplugins" class="cmtext" onclick="cmaction(8,event)" style="display:none">Plugins</div>
72:         <hr id="cxmgroupsplit" />
73:         <div id="cxstar" class="cmtext" onclick="cmaction(10,event)" style="display:none">Toggle Star</div>
74:         <div id="cxmdesktop" class="cmtext" onclick="cmaction(9,event)" style="display:none">Multi-Desktop</div>
75:     </div>
76:     <div id="meshContextMenu" class="contextMenu noselect" style="display:none;min-width:0px">
77:         <div id="cxselectall" class="cmtext" onclick="cmmeshaction(1,event)">Select All</div>
78:         <div id="cxselectnone" class="cmtext" onclick="cmmeshaction(2,event)">Select None</div>
79:         <!--
80:         <hr id="cxmgroupsplit2" style="display:none" />
81:         <div id="cxmmdesktop" class="cmtext" style="display:none" onclick="cmmeshaction(3,event)">Multi-Desktop</div>
82:         -->
83:     </div>
84:     <div id="filesShellContextMenu" class="contextMenu noselect" style="display:none;min-width:0px">
85:         <div class="cmtext" onclick="cmconnectfilesaction()">Ask Consent</div>
86:     </div>
87:     <div id="termShellContextMenu" class="contextMenu noselect" style="display:none;min-width:0px">
88:         <div class="cmtext" onclick="cmtermaction(1,0,event)"><b>Admin Shell</b></div>
89:         <div class="cmtext" onclick="cmtermaction(6,0,event)">Admin PowerShell</div>
90:         <div class="cmtext" onclick="cmtermaction(8,0,event)">User Shell</div>
91:         <div class="cmtext" onclick="cmtermaction(9,0,event)">User PowerShell</div>
92:         <div class="cmtext" onclick="cmtermaction(1,0x10,event)">Ask Admin Shell</div>
93:         <div class="cmtext" onclick="cmtermaction(6,0x10,event)">Ask Admin PowerShell</div>
94:         <div class="cmtext" onclick="cmtermaction(8,0x10,event)">Ask User Shell</div>
95:         <div class="cmtext" onclick="cmtermaction(9,0x10,event)">Ask User PowerShell</div>
96:     </div>
97:     <div id="termShellContextMenu2" class="contextMenu noselect" style="display:none;min-width:0px">
98:         <div class="cmtext" onclick="cmtermaction(8,0,event)"><b>User Shell</b></div>
99:         <div class="cmtext" onclick="cmtermaction(9,0,event)">User PowerShell</div>
```

Lines 147-306:
```javascript
147:         <span id="deskPreConfigShortcutContextMenu2"></span>
148:         <div class="cmtext" onclick="cmdeskpreconfigtypeaction(-1,event)">Customize...</div>
149:     </div>
150:     <div id="deskPreConfigScriptContextMenu" class="contextMenu noselect" style="display:none;min-width:0px;max-height:calc(80% - 50px);overflow-y:auto">
151:         <span id="deskPreConfigScriptContextMenu1"></span>
152:         <span id="deskPreConfigScriptContextMenu2"></span>
153:     </div>
154:     <!--
155:     <div id="pluginTabContextMenu" class="contextMenu noselect" style="display:none;min-width:0px">
156:         <div id="cxclose" class="cmtext" onclick="pluginTabClose(event)">Close Tab</div>
157:     </div>
158:     -->
159:     <!-- main page -->
160:     <div id=container>
161:         <div id="notifiyBox" class="notifiyBox" style="display:none"></div>
162:         <div id=masthead class=noselect>
163:             <div style="float:left">{{{titlehtml}}}</div>
164:             <div class="title" onclick="go(1,event)">{{{title1}}}</div>
165:             <div class="title2">{{{title2}}}</div>
166:             <div style="float:right">
167:                 <div id=notificationCount onclick="clickNotificationIcon()" class="unselectable" style="display: none;" title="Click to view current notifications">0</div>
168:             </div>
169:             <p id="logoutControl"><span id=logoutControlSpan class="logoncontrolspan"></span><span id=idleTimeoutNotify style="color:yellow"></span></p>
170:             <div class=textnewui id=textnewui onmouseup=toggleBootstrapUIMode() onkeypress="if (event.key=='Enter') { toggleBootstrapUIMode(); }">
171:                 <b>Try the new MeshCentral UI</b>
172:             </div>
173:         </div>
174:         <div id="page_leftbar">
175:             <div style="height:16px"></div>
176:             <div id=LeftMenuMyDevices tabindex=0 class="lbbutton lbbuttonsel" title="My Devices" onmouseup=go(1,event) onkeypress="if (event.key=='Enter') { go(1); }">
177:                 <div class="lbtg lb2"></div>
178:             </div>
179:             <div id=LeftMenuMyAccount tabindex=0 class="lbbutton" title="My Account" onmouseup=go(2,event) onkeypress="if (event.key=='Enter') { go(2); }">
180:                 <div class="lbtg lb1"></div>
181:             </div>
182:             <div id=LeftMenuMyEvents tabindex=0 class="lbbutton" title="My Events" onmouseup=go(3,event) onkeypress="if (event.key=='Enter') { go(3); }">
183:                 <div class="lbtg lb3"></div>
184:             </div>
185:             <div id=LeftMenuMyFiles tabindex=0 class="lbbutton" style="display:none" title="My Files" onmouseup=go(5,event) onkeypress="if (event.key=='Enter') { go(5); }">
186:                 <div class="lbtg lb4"></div>
187:             </div>
188:             <div id=LeftMenuMyUsers tabindex=0 class="lbbutton" style="display:none" title="My Users" onmouseup=go(4,event) onkeypress="if (event.key=='Enter') { go(4); }">
189:                 <div class="lbtg lb5"></div>
190:             </div>
191:             <div id=LeftMenuMyServer tabindex=0 class="lbbutton" style="display:none" title="My Server" onmouseup=go(6,event) onkeypress="if (event.key=='Enter') { go(6); }">
192:                 <div class="lbtg lb6"></div>
193:             </div>
194:         </div>
195:         <div id=topbar class=noselect>
196:             <div>
197:                 <div style="position:relative">
198:                     <span id=logoutControlSpan2></span>
199:                     <div tabindex=0 id=uiMenuButton title="User interface selection" onclick="showUserInterfaceSelectMenu()" onkeypress="if (event.key == 'Enter') showUserInterfaceSelectMenu()">
200:                         &diams;
201:                         <div id=uiMenu style="display:none">
202:                             <table>
203:                                 <tr>
204:                                     <td>
205:                                         <div tabindex=0 id=uiViewButton1 class=uiSelector onclick=userInterfaceSelectMenu(1) title="Left bar interface" onkeypress="if (event.key == 'Enter') userInterfaceSelectMenu(1)"><div class="uiSelector1"></div></div>
206:                                         <div tabindex=0 id=uiViewButton2 class=uiSelector onclick=userInterfaceSelectMenu(2) title="Top bar interface" onkeypress="if (event.key == 'Enter') userInterfaceSelectMenu(2)"><div class="uiSelector2"></div></div>
207:                                         <div tabindex=0 id=uiViewButton3 class=uiSelector onclick=userInterfaceSelectMenu(3) title="Fixed width interface" onkeypress="if (event.key == 'Enter') userInterfaceSelectMenu(3)"><div class="uiSelector3"></div></div>
208:                                         <div tabindex=0 id=uiViewButton7 class=uiSelector onclick=toggleBootstrapUIMode() title="Toggle Modern UI" onkeypress="if (event.key == 'Enter') toggleBootstrapUIMode()"><div class="uiSelector7"></div></div>
209:                                     </td>
210:                                     <td>
211:                                         <div tabindex=0 id=uiViewButton6 class=uiSelector onclick="showNotes(false)" title="Personal Notes" onkeypress="if (event.key == 'Enter') showNotes(false)"><div class="uiSelector6"></div></div>
212:                                         <div tabindex=0 id=uiViewButton4 class=uiSelector onclick=toggleNightMode() title="Toggle night mode" onkeypress="if (event.key == 'Enter') toggleNightMode()"><div class="uiSelector4"></div></div>
213:                                         <div tabindex=0 id=uiViewButton5 class=uiSelector onclick=toggleFooterBarMode() title="Toggle footer bar" onkeypress="if (event.key == 'Enter') toggleFooterBarMode()"><div class="uiSelector5"></div></div>
214:                                         <div tabindex=0 id=uiViewButton8 class=uiSelector onclick=toggleBrowserFullscreen() title="Browser Full Screen" onkeypress="if (event.key == 'Enter') toggleBrowserFullscreen()"><div class="viewSelector5"></div></div>
215:                                     </td>
216:                                 </tr>
217:                             </table>
218:                         </div>
219:                     </div>
220:                     <table id=MainMenuSpan cellpadding=0 cellspacing=0 class=style1>
221:                         <tr>
222:                             <td tabindex=0 id=MainMenuMyDevices class="topbar_td style3x" onmouseup=go(1,event) onkeypress="if (event.key == 'Enter') go(1)">My Devices</td>
223:                             <td tabindex=0 id=MainMenuMyAccount class="topbar_td style3x" onmouseup=go(2,event) onkeypress="if (event.key == 'Enter') go(2)">My Account</td>
224:                             <td tabindex=0 id=MainMenuMyEvents class="topbar_td style3x" onmouseup=go(3,event) onkeypress="if (event.key == 'Enter') go(3)">My Events</td>
225:                             <td tabindex=0 id=MainMenuMyFiles class="topbar_td style3x" onmouseup=go(5,event) onkeypress="if (event.key == 'Enter') go(5)">My Files</td>
226:                             <td tabindex=0 id=MainMenuMyUsers class="topbar_td style3x" onmouseup=go(4,event) onkeypress="if (event.key == 'Enter') go(4)">My Users</td>
227:                             <td tabindex=0 id=MainMenuMyServer class="topbar_td style3x" onmouseup=go(6,event) onkeypress="if (event.key == 'Enter') go(6)">My Server</td>
228:                             <td class="topbar_td_end style3">&nbsp;</td>
229:                         </tr>
230:                     </table>
231:                     <div id=MainSubMenuSpan style="display:none">
232:                         <table id=MainSubMenu cellpadding=0 cellspacing=0 class=style1>
233:                             <tr>
234:                                 <td tabindex=0 id=MainDev class="topbar_td style3x" onmouseup=go(10,event) onkeypress="if (event.key == 'Enter') go(10)">General</td>
235:                                 <td tabindex=0 id=MainDevDesktop class="topbar_td style3x" onmouseup=go(11,event) onkeypress="if (event.key == 'Enter') go(11)">Desktop</td>
236:                                 <td tabindex=0 id=MainDevTerminal class="topbar_td style3x" onmouseup=go(12,event) onkeypress="if (event.key == 'Enter') go(12)">Terminal</td>
237:                                 <td tabindex=0 id=MainDevFiles class="topbar_td style3x" onmouseup=go(13,event) onkeypress="if (event.key == 'Enter') go(13)">Files</td>
238:                                 <td tabindex=0 id=MainDevRegistry class="topbar_td style3x" onmouseup=go(9,event) onkeypress="if (event.key == 'Enter') go(9)">Registry</td>
239:                                 <td tabindex=0 id=MainDevApps class="topbar_td style3x" onmouseup=go(18,event) onkeypress="if (event.key == 'Enter') go(18)">Software</td>
240:                                 <td tabindex=0 id=MainDevEvents class="topbar_td style3x" onmouseup=go(16,event) onkeypress="if (event.key == 'Enter') go(16)">Events</td>
241:                                 <td tabindex=0 id=MainDevInfo class="topbar_td style3x" onmouseup=go(17,event) onkeypress="if (event.key == 'Enter') go(17)">Details</td>
242:                                 <td tabindex=0 id=MainDevAmt class="topbar_td style3x" onmouseup=go(14,event) onkeypress="if (event.key == 'Enter') go(14)">Intel&reg;AMT</td>
243:                                 <td tabindex=0 id=MainDevConsole class="topbar_td style3x" onmouseup=go(15,event) onkeypress="if (event.key == 'Enter') go(15)">Console</td>
244:                                 <td tabindex=0 id=MainDevPlugins class="topbar_td style3x" onmouseup=go(19,event) onkeypress="if (event.key == 'Enter') go(19)">Plugins</td>
245:                                 <td class="topbar_td_end style3">&nbsp;</td>
246:                             </tr>
247:                         </table>
248:                     </div>
249:                     <div id=MeshSubMenuSpan style="display:none">
250:                         <table id=MeshSubMenu cellpadding=0 cellspacing=0 class=style1>
251:                             <tr>
252:                                 <td tabindex=0 id=MeshGeneral class="topbar_td style3x" onmouseup=go(20,event) onkeypress="if (event.key == 'Enter') go(20)">General</td>
253:                                 <td tabindex=0 id=MeshSummary class="topbar_td style3x" onmouseup=go(21,event) onkeypress="if (event.key == 'Enter') go(21)">Summary</td>
254:                                 <td class="topbar_td_end style3">&nbsp;</td>
255:                             </tr>
256:                         </table>
257:                     </div>
258:                     <div id=EventsSubMenuSpan style="display:none">
259:                         <table id=EventsSubMenu cellpadding=0 cellspacing=0 class=style1>
260:                             <tr>
261:                                 <td tabindex=0 id=EventsLive class="topbar_td style3x" onmouseup=go(3,event) onkeypress="if (event.key == 'Enter') go(3)">Events</td>
262:                                 <td tabindex=0 id=EventsReport class="topbar_td style3x" onmouseup=go(60,event) onkeypress="if (event.key == 'Enter') go(60)">Reports</td>
263:                                 <td class="topbar_td_end style3">&nbsp;</td>
264:                             </tr>
265:                         </table>
266:                     </div>
267:                     <div id=UserSubMenuSpan style="display:none">
268:                         <table id=UserSubMenu cellpadding=0 cellspacing=0 class=style1>
269:                             <tr>
270:                                 <td tabindex=0 id=UserGeneral class="topbar_td style3x" onmouseup=go(30,event) onkeypress="if (event.key == 'Enter') go(30)">General</td>
271:                                 <td tabindex=0 id=UserEvents class="topbar_td style3x" onmouseup=go(31,event) onkeypress="if (event.key == 'Enter') go(31)">Events</td>
272:                                 <td class="topbar_td_end style3">&nbsp;</td>
273:                             </tr>
274:                         </table>
275:                     </div>
276:                     <div id=UsersSubMenuSpan style="display:none">
277:                         <table id=UsersSubMenu cellpadding=0 cellspacing=0 class=style1>
278:                             <tr>
279:                                 <td tabindex=0 id=UsersGeneral class="topbar_td style3x" onmouseup=go(4,event) onkeypress="if (event.key == 'Enter') go(4)">Users</td>
280:                                 <td tabindex=0 id=UsersGroups class="topbar_td style3x" onmouseup=go(50,event) onkeypress="if (event.key == 'Enter') go(50)">Groups</td>
281:                                 <td tabindex=0 id=UsersRecordings class="topbar_td style3x" onmouseup=go(52,event) onkeypress="if (event.key == 'Enter') go(52)">Recordings</td>
282:                                 <td class="topbar_td_end style3">&nbsp;</td>
283:                             </tr>
284:                         </table>
285:                     </div>
286:                     <div id=ServerSubMenuSpan style="display:none">
287:                         <table id=ServerSubMenu cellpadding=0 cellspacing=0 class=style1>
288:                             <tr>
289:                                 <td tabindex=0 id=ServerGeneral class="topbar_td style3x" onmouseup=go(6,event) onkeypress="if (event.key == 'Enter') go(6)">General</td>
290:                                 <td tabindex=0 id=ServerStats class="topbar_td style3x" onmouseup=go(40,event) onkeypress="if (event.key == 'Enter') go(40)">Stats</td>
291:                                 <td tabindex=0 id=ServerConsole class="topbar_td style3x" onmouseup=go(115,event) onkeypress="if (event.key == 'Enter') go(115)">Console</td>
292:                                 <td tabindex=0 id=ServerTrace class="topbar_td style3x" onmouseup=go(41,event) onkeypress="if (event.key == 'Enter') go(41)">Trace</td>
293:                                 <td tabindex=0 id=ServerPlugins class="topbar_td style3x" onmouseup=go(42,event) onkeypress="if (event.key == 'Enter') go(42)">Plugins</td>
294:                                 <td class="topbar_td_end style3">&nbsp;</td>
295:                             </tr>
296:                         </table>
297:                     </div>
298:                     <div id=UserDummyMenuSpan>
299:                         <table id=UserDummyMenu cellpadding=0 cellspacing=0 class=style1>
300:                             <tr><td class=style3 style="">&nbsp;</td></tr>
301:                         </table>
302:                     </div>
303:                 </div>
304:             </div>
305:         </div>
306:         <div id="column_l">
```

Lines 447-537:
```javascript
447:                             <div id="manageHardwareOtp"><div class="p2AccountActions"><span id="authKeySetupCheck"><strong>&#x2713;</strong></span></div><span><a href=# onclick="return account_manageHardwareOtp(0)">Manage security keys</a><br /></span></div>
448:                             <div id="managePushAuthDev"><div class="p2AccountActions"><span id="authPushAuthDevCheck"><strong>&#x2713;</strong></span></div><span><a href=# onclick="return account_managePushAuthDev()">Manage push authentication</a><br /></span></div>
449:                             <div id="manageMessaging1"><div class="p2AccountActions"><span id="authMessagingCheck"><strong>&#x2713;</strong></span></div><span><a href=# onclick="return account_manageMessaging()">Manage messaging</a><br /></span></div>
450:                             <div id="manageOtp"><div class="p2AccountActions"><span id="authCodesSetupCheck"><strong>&#x2713;</strong></span></div><span><a href=# onclick="return account_manageOtp(0)">Manage backup codes</a><br /></span></div>
451:                         </div>
452:                     </div>
453:                     <div id="p2AccountActions">
454:                         <p><strong>Account actions</strong></p>
455:                         <p class="mL">
456:                             <span id="viewPreviousLogins"><a href=# onclick="return account_viewPreviousLogins()">View previous logins</a><br /></span>
457:                             <span id="managePhoneNumber2" style="display:none"><a href=# onclick="return account_managePhone()">Manage phone number</a><br /></span>
458:                             <span id="manageMessaging2" style="display:none"><a href=# onclick="return account_manageMessaging()">Manage messaging</a><br /></span>
459:                             <span id="verifyEmailId" style="display:none"><a href=# onclick="return account_showVerifyEmail()">Verify email</a><br /></span>
460:                             <span id="accountEnableNotificationsSpan" style="display:none"><a href=# onclick="return account_enableNotifications()">Enable web notifications</a><br /></span>
461:                             <a href=# onclick="return account_showLocalizationSettings()">Localization Settings</a><br />
462:                             <a href=# onclick="return account_showAccountNotifySettings()">Notification Settings</a><br />
463:                             <span id="p2AccountPassActions">
464:                                 <span id="accountChangeEmailAddressSpan" style="display:none"><a href=# onclick="return account_showChangeEmail()">Change email address</a><br /></span>
465:                                 <a href=# onclick="return account_showChangePassword()">Change password</a><span id="p2nextPasswordUpdateTime"></span><br />
466:                                 <a href=# onclick="return account_showDeleteAccount()">Delete account</a><br />
467:                             </span>
468:                             <span id="accountCreateLoginTokenSpan" style="display:none"><a href=# onclick="return account_createLoginToken()">Create login token</a><br /></span>
469:                         </p>
470:                         <br style=clear:both />
471:                     </div>
472:                     <div id=p2logintokens></div>
473:                     <strong>Device Groups</strong>
474:                     <span id="p2createMeshLink1"> - <a href=# onclick="return account_createMesh()" class="newMeshBtn"> New</a></span>
475:                     <br /><br />
476:                     <div id=p2meshes></div>
477:                     <div id=p2noMeshFound style="display:none">No device groups.<span id="p2createMeshLink2"> <a href=# onclick="return account_createMesh()"><strong>Get started here!</strong></a></span></div>
478:                     <br style=clear:both />
479:                 </div>
480:             </div>
481:             <div id=p3 style="display:none">
482:                 <div id="p3title"><h1>My Events</h1></div>
483:                 <table class="pTable">
484:                     <tr>
485:                         <td class="h1"></td>
486:                         <td class="auto-style1">
487:                             <input type="button" style="display:none" value="Download Report" onclick=p3showReportDialog() />
488:                         </td>
489:                         <td class="auto-style1">
490:                             Filter
491:                             <select id=p3filterevents onchange=refreshEvents()>
492:                                 <option notransval=1 value="">All Logs</option>
493:                                 <option notransval=1 value=agentlog>Agent Logs</option>
494:                                 <option notransval=1 value=relaylog>Relay Logs</option>
495:                                 <option notransval=1 value=manual>Manual Logs</option>
496:                                 <option notransval=1 value=runcommands>Run Command Logs</option>
497:                                 <option notransval=1 value=batchupload>Batch Upload Logs</option>
498:                                 <option notransval=1 value=changenode>Change Node Logs</option>
499:                                 <option notransval=1 value=removenode>Remove Node Logs</option>
500:                             </select>
501:                             Show
502:                             <select id=p3limitdropdown onchange=refreshEvents()>
503:                                 <option notransval=1 value=60>Last 60</option>
504:                                 <option notransval=1 value=120>Last 120</option>
505:                                 <option notransval=1 value=250>Last 250</option>
506:                                 <option notransval=1 value=500>Last 500</option>
507:                                 <option notransval=1 value=1000>Last 1000</option>
508:                                 <option notransval=1 value="">No limit</option>
509:                             </select>&nbsp;
510:                             <a href=# onclick=p3showDownloadEventsDialog(2)><img src=images/link4.png height=10 width=10 title="Download Events" style=cursor:pointer></a>&nbsp;
511:                         </td>
512:                         <td class="h2"></td>
513:                     </tr>
514:                 </table>
515:                 <div id=p3events></div>
516:             </div>
517:             <div id=p4 style="display:none">
518:                 <div id="p4title"><h1>My Users</h1></div>
519:                 <table class="pTable">
520:                     <tr>
521:                         <td class="h1"></td>
522:                         <td class="style14">
523:                             <div style="float:right">
524:                                 <input type=button onclick=showUserBroadcastDialog() style=margin-right:6px value="Broadcast" />
525:                                 <a href=# onclick=p4downloadUserInfo()><img style="cursor:pointer" title="Download user information" src="images/link4.png" /></a>
526:                                 <a href=# onclick=p4batchAccountCreate()><img id=p4UserBatchCreate style="cursor:pointer;display:none" title="Batch create many user accounts" src="images/link6.png" /></a>
527:                                 <img style="cursor:pointer;margin-top:3px;margin-left:6px" id=usersListToolbarSettings src="images/icon-gear.png" width=16 height=16 onclick="onUsersViewSettings()" />
528:                             </div>
529:                             <div>
530:                                 <input type=button id=UsersSelectAllButton onclick="p3usersSelectallButtonFunction()" value="Select All" />
531:                                 <input type=button id=UsersGroupActionButton disabled="disabled" value="Group Action" onclick=p3usersGroupActionFunction() />
532:                                 <input id=UserNewAccountButton type=button onclick=showCreateNewAccountDialog() value="New Account..." />
533:                                 <input id=UserSearchInput type=search style=width:120px;margin-left:6px placeholder=Filter onchange=onUserSearchInputChanged() onkeyup=onUserSearchInputChanged() autocomplete=off onfocus=onUserSearchFocus(1) onblur=onUserSearchFocus(0) />
534:                             </div>
535:                         </td>
536:                         <td class="h2"></td>
537:                     </tr>
```

## package/db.js

Lines 1129-1205:
```javascript
1129:                             setTimeout(setupFileChangeStream, 10000);
1130:                         });
1131:                         obj.changeStream = true;
1132:                     };
1133:                     setupFileChangeStream();
1134:                 }
1135:             }
1136:
1137:             // Setup MongoDB events collection and indexes
1138:             obj.eventsfile = db.collection('events'); // Collection containing all events
1139:             obj.eventsfile.indexes(function (err, indexes) {
1140:                 var indexesByName = {};
1141:                 for (var i in indexes) { indexesByName[indexes[i].name] = indexes[i]; }
1142:
1143:                 // Ensure standard event indexes exist
1144:                 if (indexesByName['UseridAction1'] == null) {
1145:                     obj.eventsfile.createIndex({ userid: 1, action: 1 }, { sparse: 1, name: 'UseridAction1' });
1146:                 }
1147:                 if (indexesByName['DomainNodeTime1'] == null) {
1148:                     obj.eventsfile.createIndex({ domain: 1, nodeid: 1, time: -1 }, { sparse: 1, name: 'DomainNodeTime1' });
1149:                 }
1150:                 if (indexesByName['IdsAndTime1'] == null) {
1151:                     obj.eventsfile.createIndex({ ids: 1, time: -1 }, { sparse: 1, name: 'IdsAndTime1' });
1152:                 }
1153:                 if (indexesByName['ExpireTime1'] == null) {
1154:                     obj.eventsfile.createIndex({ time: 1 }, { expireAfterSeconds: expireEventsSeconds, name: 'ExpireTime1' });
1155:                 } else if (indexesByName['ExpireTime1'].expireAfterSeconds != expireEventsSeconds) {
1156:                     // Reset the timeout index if expiration setting changed
1157:                     obj.eventsfile.dropIndex('ExpireTime1', function (err) {
1158:                         obj.eventsfile.createIndex({ time: 1 }, { expireAfterSeconds: expireEventsSeconds, name: 'ExpireTime1' });
1159:                     });
1160:                 }
1161:
1162:                 // Indexes to prevent COLLSCAN and in-memory sort in event log queries
1163:                 if (indexesByName['DomainIdsTime1'] == null && indexesByName['domain_1_ids_1_time_-1'] == null) {
1164:                     obj.eventsfile.createIndex({ domain: 1, ids: 1, time: -1 }, { name: 'DomainIdsTime1' });               // Speeds up GetEvents() and GetEventsWithLimit()
1165:                 }
1166:                 if (indexesByName['DomainUserTime1'] == null && indexesByName['domain_1_userid_1_time_-1'] == null) {
1167:                     obj.eventsfile.createIndex({ domain: 1, userid: 1, time: -1 }, { sparse: 1, name: 'DomainUserTime1' }); // Speeds up GetUserEvents()
1168:                 }
1169:                 if (indexesByName['DomainUserActionTime1'] == null && indexesByName['domain_1_userid_1_action_1_time_-1'] == null) {
1170:                     obj.eventsfile.createIndex({ domain: 1, userid: 1, action: 1, time: -1 }, { name: 'DomainUserActionTime1' }); // Speeds up GetUserLoginEvents() and GetFailedLoginCount()
1171:                 }
1172:             });
1173:
1174:             // Setup MongoDB power events collection and indexes
1175:             obj.powerfile = db.collection('power');                                 // Collection containing all power events
1176:             obj.powerfile.indexes(function (err, indexes) {
1177:                 var indexesByName = {};
1178:                 for (var i in indexes) { indexesByName[indexes[i].name] = indexes[i]; }
1179:
1180:                 if (indexesByName['NodeIdAndTime1'] == null) {
1181:                     obj.powerfile.createIndex({ nodeid: 1, time: 1 }, { sparse: 1, name: 'NodeIdAndTime1' });
1182:                 }
1183:                 if (indexesByName['Domain1'] == null && indexesByName['domain_1'] == null) {
1184:                     obj.powerfile.createIndex({ domain: 1 }, { sparse: 1, name: 'Domain1' });                               // Avoids COLLSCAN on removeDomain()
1185:                 }
1186:                 if (indexesByName['ExpireTime1'] == null) {
1187:                     obj.powerfile.createIndex({ 'time': 1 }, { expireAfterSeconds: expirePowerEventsSeconds, name: 'ExpireTime1' });
1188:                 } else if (indexesByName['ExpireTime1'].expireAfterSeconds != expirePowerEventsSeconds) {
1189:                     // Reset the timeout index
1190:                     obj.powerfile.dropIndex('ExpireTime1', function (err) {
1191:                         // Reset the expire power events index
1192:                         obj.powerfile.createIndex({ 'time': 1 }, { expireAfterSeconds: expirePowerEventsSeconds, name: 'ExpireTime1' });
1193:                     });
1194:                 }
1195:             });
1196:
1197:             // Setup MongoDB smbios collection, no indexes needed
1198:             obj.smbiosfile = db.collection('smbios');                               // Collection containing all smbios information
1199:
1200:             // Setup MongoDB server stats collection
1201:             obj.serverstatsfile = db.collection('serverstats');                     // Collection of server stats
1202:             obj.serverstatsfile.indexes(function (err, indexes) {
1203:                 var indexesByName = {};
1204:                 for (var i in indexes) { indexesByName[indexes[i].name] = indexes[i]; }
1205:
```

Lines 1272-1348:
```javascript
1272:             if (indexesByName['Domain1'] == null && indexesByName['domain_1'] == null) {
1273:                 obj.file.createIndex({ domain: 1 }, { sparse: 1, name: 'Domain1' });                                   // Speeds up DeleteDomain() and removeDomain()
1274:             }
1275:             if (indexesByName['TypeDomainNode1'] == null && indexesByName['type_1_domain_1_nodeid_1'] == null) {
1276:                 obj.file.createIndex({ type: 1, domain: 1, nodeid: 1 }, { sparse: 1, name: 'TypeDomainNode1' });       // Speeds up GetAllTypeNodeFiltered()
1277:             }
1278:         });
1279:
1280:         // Setup MongoDB events collection and indexes
1281:         obj.eventsfile = db.collection('events');                               // Collection containing all events
1282:         obj.eventsfile.getIndexes(function (err, indexes) {
1283:             var indexesByName = {};
1284:             for (var i in indexes) { indexesByName[indexes[i].name] = indexes[i]; }
1285:
1286:             // Ensure standard event indexes exist
1287:             if (indexesByName['UseridAction1'] == null) {
1288:                 obj.eventsfile.createIndex({ userid: 1, action: 1 }, { sparse: 1, name: 'UseridAction1' });
1289:             }
1290:             if (indexesByName['DomainNodeTime1'] == null) {
1291:                 obj.eventsfile.createIndex({ domain: 1, nodeid: 1, time: -1 }, { sparse: 1, name: 'DomainNodeTime1' });
1292:             }
1293:             if (indexesByName['IdsAndTime1'] == null) {
1294:                 obj.eventsfile.createIndex({ ids: 1, time: -1 }, { sparse: 1, name: 'IdsAndTime1' });
1295:             }
1296:             if (indexesByName['ExpireTime1'] == null) {
1297:                 obj.eventsfile.createIndex({ time: 1 }, { expireAfterSeconds: expireEventsSeconds, name: 'ExpireTime1' });
1298:             } else if (indexesByName['ExpireTime1'].expireAfterSeconds != expireEventsSeconds) {
1299:                 // Reset the timeout index if expiration setting changed
1300:                 obj.eventsfile.dropIndex('ExpireTime1', function (err) {
1301:                     obj.eventsfile.createIndex({ time: 1 }, { expireAfterSeconds: expireEventsSeconds, name: 'ExpireTime1' });
1302:                 });
1303:             }
1304:
1305:             // Indexes to prevent COLLSCAN and in-memory sort in event log queries
1306:             if (indexesByName['DomainIdsTime1'] == null && indexesByName['domain_1_ids_1_time_-1'] == null) {
1307:                 obj.eventsfile.createIndex({ domain: 1, ids: 1, time: -1 }, { name: 'DomainIdsTime1' });               // Speeds up GetEvents() and GetEventsWithLimit()
1308:             }
1309:             if (indexesByName['DomainUserTime1'] == null && indexesByName['domain_1_userid_1_time_-1'] == null) {
1310:                 obj.eventsfile.createIndex({ domain: 1, userid: 1, time: -1 }, { sparse: 1, name: 'DomainUserTime1' }); // Speeds up GetUserEvents()
1311:             }
1312:             if (indexesByName['DomainUserActionTime1'] == null && indexesByName['domain_1_userid_1_action_1_time_-1'] == null) {
1313:                 obj.eventsfile.createIndex({ domain: 1, userid: 1, action: 1, time: -1 }, { name: 'DomainUserActionTime1' }); // Speeds up GetUserLoginEvents() and GetFailedLoginCount()
1314:             }
1315:         });
1316:
1317:         // Setup MongoDB power events collection and indexes
1318:         obj.powerfile = db.collection('power');                                 // Collection containing all power events
1319:         obj.powerfile.getIndexes(function (err, indexes) {
1320:             var indexesByName = {};
1321:             for (var i in indexes) { indexesByName[indexes[i].name] = indexes[i]; }
1322:
1323:             if (indexesByName['NodeIdAndTime1'] == null) {
1324:                 obj.powerfile.createIndex({ nodeid: 1, time: 1 }, { sparse: 1, name: 'NodeIdAndTime1' });
1325:             }
1326:             if (indexesByName['Domain1'] == null && indexesByName['domain_1'] == null) {
1327:                 obj.powerfile.createIndex({ domain: 1 }, { sparse: 1, name: 'Domain1' });                               // Avoids COLLSCAN on removeDomain()
1328:             }
1329:             if (indexesByName['ExpireTime1'] == null) {
1330:                 obj.powerfile.createIndex({ 'time': 1 }, { expireAfterSeconds: expirePowerEventsSeconds, name: 'ExpireTime1' });
1331:             } else if (indexesByName['ExpireTime1'].expireAfterSeconds != expirePowerEventsSeconds) {
1332:                 // Reset the timeout index
1333:                 obj.powerfile.dropIndex('ExpireTime1', function (err) {
1334:                     // Reset the expire power events index
1335:                     obj.powerfile.createIndex({ 'time': 1 }, { expireAfterSeconds: expirePowerEventsSeconds, name: 'ExpireTime1' });
1336:                 });
1337:             }
1338:         });
1339:
1340:         // Setup MongoDB smbios collection, no indexes needed
1341:         obj.smbiosfile = db.collection('smbios');                               // Collection containing all smbios information
1342:
1343:         // Setup MongoDB server stats collection
1344:         obj.serverstatsfile = db.collection('serverstats');                     // Collection of server stats
1345:         obj.serverstatsfile.getIndexes(function (err, indexes) {
1346:             var indexesByName = {};
1347:             for (var i in indexes) { indexesByName[indexes[i].name] = indexes[i]; }
1348:
```

Lines 1764-1937:
```javascript
1764:                 sqlDbQuery('SELECT doc FROM main WHERE domain = $1 AND extra = $2', [domain, 'email/' + email], function (err, docs) {
1765:                     if (docs != null) { for (var i in docs) { delete docs[i].type; if (docs[i].links != null) { docs[i] = common.unEscapeLinksFieldName(docs[i]); } } }
1766:                     func(err, performTypedRecordDecrypt(docs));
1767:                 });
1768:             }
1769:             obj.GetUserWithVerifiedEmail = function (domain, email, func) {
1770:                 sqlDbQuery('SELECT doc FROM main WHERE domain = $1 AND extra = $2', [domain, 'email/' + email], function (err, docs) {
1771:                     if (docs != null) { for (var i in docs) { delete docs[i].type; if (docs[i].links != null) { docs[i] = common.unEscapeLinksFieldName(docs[i]); } } }
1772:                     func(err, performTypedRecordDecrypt(docs));
1773:                 });
1774:             }
1775:             obj.Remove = function (id, func) { sqlDbQuery('DELETE FROM main WHERE id = $1', [id], func); };
1776:             obj.RemoveAll = function (func) { sqlDbQuery('DELETE FROM main', null, func); };
1777:             obj.RemoveAllOfType = function (type, func) { sqlDbQuery('DELETE FROM main WHERE type = $1', [type], func); };
1778:             obj.InsertMany = function (data, func) { var pendingOps = 0; for (var i in data) { pendingOps++; obj.SetRaw(data[i], function () { if (--pendingOps == 0) { func(); } }); } }; // Insert records directly, no link escaping
1779:             obj.RemoveMeshDocuments = function (id, func) { sqlDbQuery('DELETE FROM main WHERE extra = $1', [id], function () { sqlDbQuery('DELETE FROM main WHERE id = $1', ['nt' + id], func); }); };
1780:             obj.MakeSiteAdmin = function (username, domain) { obj.Get('user/' + domain + '/' + username, function (err, docs) { if ((err == null) && (docs.length == 1)) { docs[0].siteadmin = 0xFFFFFFFF; obj.Set(docs[0]); } }); };
1781:             obj.DeleteDomain = function (domain, func) { sqlDbQuery('DELETE FROM main WHERE domain = $1', [domain], func); };
1782:             obj.SetUser = function (user) { if (user == null) return; if (user.subscriptions != null) { var u = Clone(user); if (u.subscriptions) { delete u.subscriptions; } obj.Set(u); } else { obj.Set(user); } };
1783:             obj.dispose = function () { for (var x in obj) { if (obj[x].close) { obj[x].close(); } delete obj[x]; } };
1784:             obj.getLocalAmtNodes = function (func) {
1785:                 sqlDbQuery('SELECT doc FROM main WHERE (type = \'node\') AND (extraex IS NULL)', null, function (err, docs) {
1786:                     if (docs != null) { for (var i in docs) { if (docs[i].links != null) { docs[i] = common.unEscapeLinksFieldName(docs[i]); } } }
1787:                     var r = []; if (err == null) { for (var i in docs) { if (docs[i].host != null && docs[i].intelamt != null) { r.push(docs[i]); } } } func(err, r);
1788:                 });
1789:             };
1790:             obj.getAmtUuidMeshNode = function (domainid, mtype, uuid, func) {
1791:                 sqlDbQuery('SELECT doc FROM main WHERE domain = $1 AND extraex = $2', [domainid, 'uuid/' + uuid], function (err, docs) {
1792:                     if (docs != null) { for (var i in docs) { if (docs[i].links != null) { docs[i] = common.unEscapeLinksFieldName(docs[i]); } } }
1793:                     func(err, docs);
1794:                 });
1795:             };
1796:             obj.isMaxType = function (max, type, domainid, func) { if (max == null) { func(false); } else { sqlDbExec('SELECT COUNT(id) FROM main WHERE domain = $1 AND type = $2', [domainid, type], function (err, response) { func((response['COUNT(id)'] == null) || (response['COUNT(id)'] > max), response['COUNT(id)']) }); } }
1797:
1798:             // Database actions on the events collection
1799:             obj.GetAllEvents = function (func) {
1800:                 sqlDbQuery('SELECT doc FROM events', null, func);
1801:             };
1802:             obj.StoreEvent = function (event, func) {
1803:                 obj.dbCounters.eventsSet++;
1804:                 sqlDbQuery('INSERT INTO events VALUES (NULL, $1, $2, $3, $4, $5, $6) RETURNING id', [event.time, ((typeof event.domain == 'string') ? event.domain : null), event.action, event.nodeid ? event.nodeid : null, event.userid ? event.userid : null, JSON.stringify(event)], function (err, docs) {
1805:                     if(func){ func(); }
1806:                     if ((err == null) && (docs[0].id)) {
1807:                         for (var i in event.ids) {
1808:                             if (event.ids[i] != '*') {
1809:                                 obj.pendingTransfer++;
1810:                                 sqlDbQuery('INSERT INTO eventids VALUES ($1, $2)', [docs[0].id, event.ids[i]], function(){ if(func){ func(); } });
1811:                             }
1812:                         }
1813:                     }
1814:                 });
1815:             };
1816:             obj.GetEvents = function (ids, domain, filter, func) {
1817:                 var query = "SELECT doc FROM events ";
1818:                 var dataarray = [domain];
1819:                 if (ids.indexOf('*') >= 0) {
1820:                     query = query + "WHERE (domain = $1";
1821:                     if (filter != null) {
1822:                         query = query + " AND action = $2";
1823:                         dataarray.push(filter);
1824:                     }
1825:                     query = query + ") ORDER BY time DESC";
1826:                 } else {
1827:                     query = query + 'JOIN eventids ON id = fkid WHERE (domain = $1 AND (target IN (' + dbMergeSqlArray(ids) + '))';
1828:                     if (filter != null) {
1829:                         query = query + " AND action = $2";
1830:                         dataarray.push(filter);
1831:                     }
1832:                     query = query + ") GROUP BY id ORDER BY time DESC ";
1833:                 }
1834:                 sqlDbQuery(query, dataarray, func);
1835:             };
1836:             obj.GetEventsWithLimit = function (ids, domain, limit, filter, func) {
1837:                 var query = "SELECT doc FROM events ";
1838:                 var dataarray = [domain];
1839:                 if (ids.indexOf('*') >= 0) {
1840:                     query = query + "WHERE (domain = $1";
1841:                     if (filter != null) {
1842:                         query = query + " AND action = $2) ORDER BY time DESC LIMIT $3";
1843:                         dataarray.push(filter);
1844:                     } else {
1845:                         query = query + ") ORDER BY time DESC LIMIT $2";
1846:                     }
1847:                 } else {
1848:                     query = query + "JOIN eventids ON id = fkid WHERE (domain = $1 AND (target IN (" + dbMergeSqlArray(ids) + "))";
1849:                     if (filter != null) {
1850:                         query = query + " AND action = $2) GROUP BY id ORDER BY time DESC LIMIT $3";
1851:                         dataarray.push(filter);
1852:                     } else {
1853:                         query = query + ") GROUP BY id ORDER BY time DESC LIMIT $2";
1854:                     }
1855:                 }
1856:                 dataarray.push(limit);
1857:                 sqlDbQuery(query, dataarray, func);
1858:             };
1859:             obj.GetUserEvents = function (ids, domain, userid, filter, func) {
1860:                 var query = "SELECT doc FROM events ";
1861:                 var dataarray = [domain, userid];
1862:                 if (ids.indexOf('*') >= 0) {
1863:                     query = query + "WHERE (domain = $1 AND userid = $2";
1864:                     if (filter != null) {
1865:                         query = query + " AND action = $3";
1866:                         dataarray.push(filter);
1867:                     }
1868:                     query = query + ") ORDER BY time DESC";
1869:                 } else {
1870:                     query = query + 'JOIN eventids ON id = fkid WHERE (domain = $1 AND userid = $2 AND (target IN (' + dbMergeSqlArray(ids) + '))';
1871:                     if (filter != null) {
1872:                         query = query + " AND action = $3";
1873:                         dataarray.push(filter);
1874:                     }
1875:                     query = query + ") GROUP BY id ORDER BY time DESC";
1876:                 }
1877:                 sqlDbQuery(query, dataarray, func);
1878:             };
1879:             obj.GetUserEventsWithLimit = function (ids, domain, userid, limit, filter, func) {
1880:                 var query = "SELECT doc FROM events ";
1881:                 var dataarray = [domain, userid];
1882:                 if (ids.indexOf('*') >= 0) {
1883:                     query = query + "WHERE (domain = $1 AND userid = $2";
1884:                     if (filter != null) {
1885:                         query = query + " AND action = $3) ORDER BY time DESC LIMIT $4";
1886:                         dataarray.push(filter);
1887:                     } else {
1888:                         query = query + ") ORDER BY time DESC LIMIT $3";
1889:                     }
1890:                 } else {
1891:                     query = query + "JOIN eventids ON id = fkid WHERE (domain = $1 AND userid = $2 AND (target IN (" + dbMergeSqlArray(ids) + "))";
1892:                     if (filter != null) {
1893:                         query = query + " AND action = $3) GROUP BY id ORDER BY time DESC LIMIT $4";
1894:                         dataarray.push(filter);
1895:                     } else {
1896:                         query = query + ") GROUP BY id ORDER BY time DESC LIMIT $3";
1897:                     }
1898:                 }
1899:                 dataarray.push(limit);
1900:                 sqlDbQuery(query, dataarray, func);
1901:             };
1902:             obj.GetEventsTimeRange = function (ids, domain, msgids, start, end, func) {
1903:                 if (ids.indexOf('*') >= 0) {
1904:                     sqlDbQuery('SELECT doc FROM events WHERE ((domain = $1) AND (time BETWEEN $2 AND $3)) ORDER BY time', [domain, start, end], func);
1905:                 } else {
1906:                     sqlDbQuery('SELECT doc FROM events JOIN eventids ON id = fkid WHERE ((domain = $1) AND (target IN (' + dbMergeSqlArray(ids) + ')) AND (time BETWEEN $2 AND $3)) GROUP BY id ORDER BY time', [domain, start, end], func);
1907:                 }
1908:             };
1909:             //obj.GetUserLoginEvents = function (domain, userid, func) { } // TODO
1910:             obj.GetNodeEventsWithLimit = function (nodeid, domain, limit, filter, func) {
1911:                 var query = "SELECT doc FROM events WHERE (nodeid = $1 AND domain = $2";
1912:                 var dataarray = [nodeid, domain];
1913:                 if (filter != null) {
1914:                     query = query + " AND action = $3) ORDER BY time DESC LIMIT $4";
1915:                     dataarray.push(filter);
1916:                 } else {
1917:                     query = query + ") ORDER BY time DESC LIMIT $3";
1918:                 }
1919:                 dataarray.push(limit);
1920:                 sqlDbQuery(query, dataarray, func);
1921:             };
1922:             obj.GetNodeEventsSelfWithLimit = function (nodeid, domain, userid, limit, filter, func) {
1923:                 var query = "SELECT doc FROM events WHERE (nodeid = $1) AND (domain = $2) AND ((userid = $3) OR (userid IS NULL)) ";
1924:                 var dataarray = [nodeid, domain, userid];
1925:                 if (filter != null) {
1926:                     query = query + "AND (action = $4) ORDER BY time DESC LIMIT $5";
1927:                     dataarray.push(filter);
1928:                 } else {
1929:                     query = query + "ORDER BY time DESC LIMIT $4";
1930:                 }
1931:                 dataarray.push(limit);
1932:                 sqlDbQuery(query, dataarray, func);
1933:             };
1934:             obj.RemoveAllEvents = function (domain) { sqlDbQuery('DELETE FROM events', null, function (err, docs) { }); };
1935:             obj.RemoveAllNodeEvents = function (domain, nodeid) { if ((domain == null) || (nodeid == null)) return; sqlDbQuery('DELETE FROM events WHERE domain = $1 AND nodeid = $2', [domain, nodeid], function (err, docs) { }); };
1936:             obj.RemoveAllUserEvents = function (domain, userid) { if ((domain == null) || (userid == null)) return; sqlDbQuery('DELETE FROM events WHERE domain = $1 AND userid = $2', [domain, userid], function (err, docs) { }); };
1937:             obj.GetFailedLoginCount = function (userid, domainid, lastlogin, func) { sqlDbQuery('SELECT COUNT(*) FROM events WHERE action = \'authfail\' AND domain = $1 AND userid = $2 AND time > $3', [domainid, userid, lastlogin], function (err, response) { func(err == null ? response[0]['COUNT(*)'] : 0); }); }
```

## Implementation review checklist

- Match the server history request and response fields exactly; do not infer a contract from live event packets.
- Determine what constitutes all visible events for the authenticated user; never claim a complete server audit log without evidence.
- Confirm server-side authorization and distinguish explicit denial, unsupported requests, malformed responses, timeout and empty history.
- Verify bounded queries, pagination or continuation support and ordering before exposing older-event controls.
- Verify request correlation. If legacy replies lack a correlation field, isolate one request per channel and retire the channel before reuse after timeout.
- Bind data to server and actor, reject stale callbacks, and clear history on identity or authority changes.
- Keep Activity behavior and command output intact; do not turn observed events into action-success receipts.
- Keep the first integration read-only; do not expose clear-events or server-log deletion.
- Implement Spanish/English resources and reuse ManagementSectionTabs.
- Before publishing application sources: execute JVM tests, lint, debug and Android-test compilation, and emulator Compose/instrumentation regressions. Automated UI checks are not a real-device visual review.
