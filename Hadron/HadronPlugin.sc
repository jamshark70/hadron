HadronPlugin
{
	var <inBusses, <mainOutBusses, <outBusses, <group, <uniqueID, <parentApp,
	<outerWindow, window, <>oldWinBounds, <>isHidden, <name, <ident,
	<>inConnections, <>outConnections, <dummyInBusses, <conWindow,
	<>saveGets, <>saveSets, <extraArgs, <boundCanvasItem, <helpString,
	<modSets, <modGets, <modMapSets;

	var badValueSynth, badValueResp;

	classvar <>plugins; //holder for external plugins

	shouldCheckBad { ^outBusses.size > 0 }

	*initClass
	{
		this.plugins = List.new;
	}

	*doOnServerBoot {
		(1..2).do { |numChan|
			SynthDef("hrCheckBad" ++ numChan, { |uniqueID|
				var indices = (0..numChan-1),
				inbuses = Array.fill(numChan, { |i|
					NamedControl.kr("inBus" ++ i, 0)
				}),
				pr_outbuses = Array.fill(numChan, { |i|
					NamedControl.kr("pr_outBus" ++ i, 0)
				}),
				outbuses = Array.fill(numChan, { |i|
					NamedControl.kr("outBus" ++ i, 0)
				}),
				sig = In.ar(inbuses, 1),
				bad = CheckBadValues.ar(sig, id: indices, post: 0),
				silent = Silent.ar(1),
				channelFlags = 0;
				bad.do { |badChan, i|
					// i is an int, not a ugen, so << is ok
					channelFlags = channelFlags + ((badChan > 0) * (1 << i));
				};
				SendReply.ar(channelFlags, '/hrBadValue', channelFlags, replyID: uniqueID);
				pr_outbuses.do { |bus, i|
					var chan = Select.ar(bad[i], [sig[i], silent]);
					ReplaceOut.ar(bus, chan);
					Out.ar(outbuses[i], chan);
				};
			}).add;
		}
	}

	*addHadronPlugin
	{
		Class.initClassTree(HadronPlugin);
		HadronPlugin.plugins.add(this);
	}

	*new
	{|argParentApp, argName, argIdent, argUniqueID, argExtraArgs, argBounds, argNumIns, argNumOuts, argCanvasXY|

		^super.new.doInit(argParentApp, argName, argIdent, argUniqueID, argExtraArgs, argBounds, argNumIns, argNumOuts, argCanvasXY);
	}

	doInit
	{|argParentApp, argName, argIdent, argUniqueID, argExtraArgs, argBounds, argNumIns, argNumOuts, argCanvasXY|
		var busArgFunc, needsScroll = false;

		extraArgs = argExtraArgs;
		modGets = Dictionary.new;
		modSets = Dictionary.new;
		modMapSets = Dictionary.new;

		helpString = "No help available for this plugin.";
		//every connecting plugin gets inputs from the plugin it connects.
		inBusses = Array.fill(argNumIns, { Bus.audio(Server.default, 1); });
		//plugin outputs go to a local-out first, for bad value checking, mixing, fx
		outBusses = Array.fill(argNumOuts, { Bus.audio(Server.default, 1); });
		//outputs are blackholed by default. will get an input when connected to stg.
		//the bad-value synth above passes the output to the "real" target
		mainOutBusses = Array.fill(argNumOuts, { argParentApp.blackholeBus; });

		//these hold connection info. [0] is connected app, [1] is bus number in connected app.
		inConnections = Array.fill(argNumIns, { [nil, nil]; });
		outConnections = Array.fill(argNumOuts, { [nil, nil] });

		group = Group.new;
		parentApp = argParentApp;
		uniqueID = argUniqueID ? parentApp.prGiveUniqueId(this);
		isHidden = false;
		//argCanvasXY.class.postln;
		boundCanvasItem = HadronCanvasItem(parentApp.canvasObj, this, argCanvasXY.x, argCanvasXY.y);

		busArgFunc = { |name, buses|
			[
				[name.asString, (0 .. buses.size-1)].flop.collect({ |row| row.join.asSymbol }),
				buses
			].flop;
		};

		if(this.shouldCheckBad) {
			badValueSynth ?? {
				badValueSynth = Synth("hrCheckBad" ++ argNumOuts,
					flat([uniqueID: uniqueID]
					++ busArgFunc.("inBus", outBusses)
					++ busArgFunc.("outBus", mainOutBusses)
					++ busArgFunc.("pr_outBus", outBusses)),
					group, \addToTail
				);
			};
			badValueResp ?? {
				badValueResp = OSCpathResponder(Server.default.addr,
					['/hrBadValue', badValueSynth.nodeID],
					{ |time, resp, msg|
						var channels = msg[3].asInteger.asBinaryString(argNumOuts),
						badchannels = Array(argNumOuts);
						channels.reverseDo { |flag, i|
							if(flag == $1) { badchannels.add(i+1) };
						};
						this.handleBadValue(badchannels);
					}
				).add;
			};
		};

		name = argName;
		ident = argIdent; //ident is to identify an instance when there is more than one instance.

		conWindow = Window.new.close; //hacky but it should respond to .isClosed true by default...

		saveGets = nil; //functions in this list will be evaulated and return value will be saved with patch
		saveSets = nil; //saved values will be injected back into instance with these functions (argument will be the saved value)

		oldWinBounds = Rect(argBounds.left, max(argBounds.top, 0),
			argBounds.width,
			argBounds.height + 40 + (30 * binaryValue(argBounds.width < 430))
		);
		// if true, title bar may be off the top of the screen, bad
		// leave 100 pix padding
		if(oldWinBounds.bottom > (Window.screenBounds.height - 100)) {
			if(oldWinBounds.top >= (oldWinBounds.bottom - Window.screenBounds.height + 100)) {
				oldWinBounds.top = (oldWinBounds.bottom - Window.screenBounds.height + 100);
			} {
				needsScroll = true;
				oldWinBounds.top = 0;
				oldWinBounds.height = Window.screenBounds.height - 100;
			};
		};
		outerWindow = Window(argName + ident, oldWinBounds, resizable: false)
		.userCanClose_(false)
		.acceptsMouseOver_(true);

		outerWindow.view.keyDownAction_
		({|...args|

			//SwingOSC has different key bindings

			//args.postln;
			if(GUI.id == \swing,
			{
				args[2].switch(131072, { args[2] = 131074; }); //keyboard bindings
			});

			if((args[1] == $H) and: { args[2] == 131074 }, //if shift+h, show help
			{
				{ this.class.openHelpFile; }.defer;
			});

		});

		Button(outerWindow, Rect(oldWinBounds.width - 80, oldWinBounds.height - 30, 50, 20))
		.states_([["Hide"]])
		.action_({ this.prHideWindow; });

		Button(outerWindow, Rect(oldWinBounds.width - 25, oldWinBounds.height - 30, 15, 20))
		.states_([["?"]])
		.action_({ this.class.openHelpFile; });

		Button(outerWindow, Rect(oldWinBounds.width - 160, oldWinBounds.height - 30, 70, 20))
		.states_([["Kill"]])
		.action_
		({
			var tempWin; //can be modal but meh. does SwingOSC have it?
			tempWin = Window("Are you Sure?", Rect(400, 400, 190, 100), resizable: false);
			StaticText(tempWin, Rect(0, 10, 190, 20)).string_("This instance will be killed!").align_(\center);
			StaticText(tempWin, Rect(0, 30, 190, 20)).string_(name + ident).align_(\center);
			Button(tempWin, Rect(10, 60, 80, 20)).states_([["Ok"]]).action_({ tempWin.close; this.selfDestruct; });
			Button(tempWin, Rect(100, 60, 80, 20)).states_([["Cancel"]]).action_({ tempWin.close; });

			tempWin.front;
		});

		Button(outerWindow, Rect(10, oldWinBounds.height - 30, 70, 20))
		.states_([["In/Outs"]])
		.action_({ this.prShowConnections; })
		.visible_(if((inBusses.size == 0) and: { outBusses.size == 0; }, { false; }, { true; }));

		if(argBounds.width >= 430) {
			StaticText(outerWindow, Rect(
				90, oldWinBounds.height - 30,
				50, 20
			)).string_("name");
			TextField(outerWindow, Rect(
				150, oldWinBounds.height - 30,
				oldWinBounds.width - 320, 20
			)).string_(ident)
			.action_({ |view|
				this.ident = view.value
			});
		} {
			StaticText(outerWindow, Rect(
				10, oldWinBounds.height - 60,
				50, 20
			)).string_("name");
			TextField(outerWindow, Rect(
				70, oldWinBounds.height - 60,
				oldWinBounds.width - 80, 20
			)).string_(ident)
			.action_({ |view|
				this.ident = view.value
			});
		};

		if(needsScroll) {
			window = ScrollView(outerWindow, Rect(0, 0, argBounds.width, oldWinBounds.height - 40 - (30 * binaryValue(argBounds.width < 430))));
		} {
			window = CompositeView(outerWindow, Rect(0, 0, argBounds.width, oldWinBounds.height - 40 - (30 * binaryValue(argBounds.width < 430))));
		};
		outerWindow.front;

	}

	prHideWindow
	{
		oldWinBounds = outerWindow.bounds;
		outerWindow.bounds = Rect(0, 0, 0, 0);
		isHidden = true;
		parentApp.isDirty = true;
		if(GUI.id != \cocoa, { outerWindow.visible_(false); });
	}

	showWindow
	{
		if(isHidden, { outerWindow.bounds = oldWinBounds; isHidden = false; });
		if(GUI.id != \cocoa, { outerWindow.visible_(true); });
		parentApp.isDirty = true;
		outerWindow.front;
	}

	prShowConnections
	{|argOptionalBounds|

		var numPorts = inBusses.size + outBusses.size; //to determine window height.
		var curRow = 0;
		var tempMenuItems = List.new;


		conWindow =
		Window("In/Outs for:" + name + ident, argOptionalBounds ? Rect(300, 300, 400, 50 + (numPorts * 20)), resizable: false);


		inConnections.do
		({|conArray, count|

			StaticText(conWindow, Rect(10, (curRow * 20) + 10, 390, 20))
			.string_
			({
				if(conArray[0].notNil,
				{
					"In." + count.asString + ":" + conArray[0].name + conArray[0].ident + conArray[1].asString;
				},
				{
					"In." + count.asString + ":" + "nothing...";
				})
			}.value);

			curRow = curRow + 1;

		});

		curRow = curRow + 1;

		//prepare menu items

		parentApp.alivePlugs.do
		({|app|
			app.inBusses.do
			({|argBus, cnt|

				tempMenuItems.add([app, cnt, app.name + app.ident, cnt.asString]);
			})
		});

		//populate menu items

		outConnections.do
		({|conArray, count|

			var tempIndex = nil;

			StaticText(conWindow, Rect(10, (curRow * 20) + 10, 100, 20))
			.string_
			({
				"Out." + count.asString + ":";
			}.value);


			PopUpMenu(conWindow, Rect(80, (curRow * 20) + 10, 200, 20))
			.items_(["Nothing."] ++ tempMenuItems.collect({|item| (item[2] + item[3]); }))
			.value_
			({
				if(conArray[0].isNil or: { conArray[1].isNil },
				{
					0; //if outConnections is [nil, nil] not connected to anything, index 0 is "Disconnected"
				},
				{
					tempMenuItems.do
					({|tItem, tCount|

						if(tItem[0] === conArray[0] and: { tItem[1] === conArray[1] },
						{
							tempIndex = tCount + 1; //+1 to compansate for the "Nothing" entry.
						});
					});
					tempIndex;
				});
			}.value)
			.action_
			({|menu|


				if(menu.value != 0,
				{//if selection is not "Disconnected"
					this.setOutBus(tempMenuItems[menu.value-1][0], tempMenuItems[menu.value-1][1], count);
				},
				{//if selection is "Disconnected"... .setOutBus will catch the nils and do appropriate action.
					this.setOutBus(nil, nil, count);
				});

				//refresh all open In/Outs windows.
				parentApp.alivePlugs.do({|plug| if(plug.conWindow.isClosed.not, { plug.refreshConWindow; }) });

			});

			curRow = curRow + 1;

		});



		conWindow.front;

	}

	refreshConWindow
	{//to redraw contents.
		var tempBounds;
		tempBounds = conWindow.bounds;
		conWindow.close;
		this.prShowConnections(tempBounds);
	}

	setOutBus
	{|argTargetPlugin, argTargetBusNo, argMyBusNo|

		var tempPlugin;
		//[argTargetPlugin, argTargetBusNo, argMyBusNo].postln;
		parentApp.isDirty = true;
		//if "Disconnected" is selected
		if(argTargetPlugin == nil,
		{
			//outConnections[argMyBusNo][0].inBusses[outConnections[argMyBusNo][1]] =
			//	outConnections[argMyBusNo][0].dummyInBusses[outConnections[argMyBusNo][1]];

			//remove old binding to this output from target plugin
			outConnections[argMyBusNo][0].inConnections[outConnections[argMyBusNo][1]] = [nil, nil];

			//remove my binding
			outConnections[argMyBusNo] = [nil, nil];
			//redirect my bus to blackhole.
			mainOutBusses[argMyBusNo] = parentApp.blackholeBus;
			this.prUpdateBusConnections;
			parentApp.canvasObj.drawCables;
			//no need to call .prUpdateBusConnections on (old) outConnections[argMyBusNo][0]
			//no further action needed, return.
			^this;
		});

		//if the target plugins input is connected to my chosen output, we need to clear it.
		argTargetPlugin.inConnections.do
		({|item, count|

			if((item[0] === this) and: { item[1] === argMyBusNo },
			{//inBusses in plugins are not altered in anyway so just delete the binding.
			//.prUpdateBusConnections on target will be called later but probably not necessary for there.
				argTargetPlugin.inConnections[count] = [nil, nil];
			});
		});

		//if the connection in target plugin is not free, we need to take several actions.
		if(argTargetPlugin.inConnections[argTargetBusNo] != [nil, nil],
		{
			tempPlugin = argTargetPlugin.inConnections[argTargetBusNo][0];
			//remove the old binding from the plugin that connects to the input of the target plugin.
			argTargetPlugin.inConnections[argTargetBusNo][0]
				.outConnections[argTargetPlugin.inConnections[argTargetBusNo][1]] = [nil, nil];

			//redirect its busses to blackhole.
			argTargetPlugin.inConnections[argTargetBusNo][0]
				.mainOutBusses[argTargetPlugin.inConnections[argTargetBusNo][1]] = parentApp.blackholeBus;

			//the farside plugin needs an update
			tempPlugin.prUpdateBusConnections;
		});

		//if we are already connected to stg, notify the target, she does not need .updateConnections, just notifying.
		if(outConnections[argMyBusNo] != [nil, nil],
		{
			outConnections[argMyBusNo][0].inConnections[outConnections[argMyBusNo][1]] = [nil, nil];
		});




		//change my out port to match the relevant input.
		mainOutBusses[argMyBusNo] = argTargetPlugin.inBusses[argTargetBusNo];
		//change the binding appropriately.
		outConnections[argMyBusNo] = [argTargetPlugin, argTargetBusNo];

		//bind the target to me.
		argTargetPlugin.inConnections[argTargetBusNo] = [this, argMyBusNo];

		argTargetPlugin.prUpdateBusConnections;
		this.prUpdateBusConnections;
		parentApp.canvasObj.drawCables;

	}

	wakeConnections
	{//interpret inConnections and outConnections after a set is loaded from file. not to be overridden.

		outConnections.do
		{|item, count|

			if(item[0] != nil,
			{
				mainOutBusses[count] = item[0].inBusses[item[1]];

			});
		}



	}

	selfDestruct
	{
		parentApp.alivePlugs.do(_.notifyPlugKill(this));
		parentApp.isDirty = true;
		badValueResp.remove;
		this.cleanUp;
		group.free;

		inConnections.do
		({|item, count|

			if(item != [nil, nil],
			{
				item[0].outConnections[item[1]] = [nil, nil];
				item[0].mainOutBusses[item[1]] = parentApp.blackholeBus;
				item[0].prUpdateBusConnections;
			});
		});

		outConnections.do
		({|item, count|

			if(item != [nil, nil],
			{
				item[0].inConnections[item[1]] = [nil, nil];
				item[0].prUpdateBusConnections;
			});
		});

		inBusses.do(_.free);
		outBusses.do(_.free);

		if(conWindow.isClosed.not, { conWindow.close; });

		parentApp.alivePlugs.remove(this);
		parentApp.alivePlugs.do({|plug| if(plug.conWindow.isClosed.not, { plug.refreshConWindow; }) });
		parentApp.prActiveMenuUpdate;
		parentApp.idPlugDict.removeAt(uniqueID);
		boundCanvasItem.removeFromCanvas;
		outerWindow.close;

		NotificationCenter.notify(this, \selfDestructed);
	}

	prUpdateBusConnections {
		if(badValueSynth.notNil) {
			badValueSynth.set(*(
				[
					["outBus", (0 .. mainOutBusses.size-1)]
					.flop.collect({ |row| row.join.asSymbol }),
					mainOutBusses
				].flop.flat
			));
		};
		this.updateBusConnections;
	}

	updateBusConnections
	{//called each time a connection changes relating to this instance
		"Plugin needs to update necessary bus mappings in updateBusConnections() method.".postln;
	}

	makeSynth {
		// called from the plugin's init method,
		// AND if a bad value is detected, to kill the offender and replace it
		"Plugin needs to create its synth(s) in the makeSynth() method.".postln;
	}

	cleanUp
	{//called when plugin (this instance) is being killed
		"You need to do your cleanup by overriding cleanUp()".postln;
	}

	notifyPlugKill
	{|argPlugin|
		//when any plugin is killed, all alive plugins receive this to help them
		//update state and gui stuff that reflects the current environment.
		//plugins concerned with that needs to override this method with their own.


	}
	notifyPlugAdd
	{|argPlugin|
		//when any plugin is added to the system, all alive plugins receive this to help them
		//update state and gui stuff that reflects the current environment.
		//plugins concerned with that needs to override this method with their own.

	}

	wakeFromLoad
	{
		//called after all plugins are finished loading. If a plugin needs to update its menus and
		//alike, representing the current state of the environment, it should override this and
		//wake from load operation.
	}

	giveSaveValues
	{
		var temp = List.new;

		saveGets.do({|item| temp.add(item.value) });
		^temp;
	}

	collide
	{
		//executed when the collide button is pressed from the main gui. override if necessary.
	}

	injectSaveValues
	{|argStrValArray|

		var temp = argStrValArray.collect({|item| item.interpret; });
		temp.do
		({|item, count|

			saveSets[count].value(item);
		});
	}

	handleBadValue { |badchannels|
		var msg = "BAD VALUE FOUND in %%, id %, channel% %.\nPlease correct the plugin's settings and click OK."
		.format(
			this.class.name,
			if(name.asSymbol == this.class.name) { "" } { " " ++ name },
			uniqueID, if(badchannels.size > 1) { "s" } { "" }, badchannels
		),
		tempwin;

		this.releaseSynth;
		Char.nl.post;
		msg.warn;
		msg = msg.split(Char.nl);

		defer {
			var sbounds = Window.screenBounds, registration;
			registration = NotificationCenter.register(this, \selfDestructed, UniqueID.next, {
				tempwin.close;
			});
			this.showWindow;
			tempwin = Window("BAD VALUE FOUND",
				Rect.aboutPoint(sbounds.center, 320, 50))
				.userCanClose_(false);
			StaticText(tempwin, Rect(2, 2, 636, 30))
				.align_(\center)
				.font_(Font.default.boldVariant.size_(14))
				.stringColor_(Color.red(0.4))
				.background_(Color.clear)
				.string_(msg[0]);
			StaticText(tempwin, Rect(2, 32, 636, 30))
				.align_(\center)
				.font_(Font.default.boldVariant.size_(14))
				.stringColor_(Color.red(0.4))
				.background_(Color.clear)
				.string_(msg[1]);
			Button(tempwin, Rect(280, 70, 80, 20))
				.states_([["OK"]])
				.action_({
					registration.remove;
					tempwin.close;
					this.makeSynth;
				});
			tempwin.front;
		};
	}

	ident_ { |string|
		ident = string;
		{
			outerWindow.name = name + ident;
			parentApp.alivePlugs.do { |plug|
				if(plug !== this) { plug.notifyIdentChanged(this, ident) };
			};
		}.defer;
	}

	// one plugin might use another plugin's 'ident' in a gui
	// override this to receive notifications of that
	// args are: the plugin whose ident changed, and the new name
	// a plugin will not be notified of its own name changing
	notifyIdentChanged { |plugin, newIdent|
	}

	// a default implementation, inactive if there is no synthInstance
	// you may override
	mapModCtl { |paramName, ctlBus|
		var node;
		if((node = this.tryPerform(\synthInstance)).notNil) {
			node.map(paramName, ctlBus);
		};
	}
}