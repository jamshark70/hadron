HrPolyPattern : HadronPlugin {
	var <key, <targetPlugin, iMadePdefs, streamCtl;
	var pdefMenu, newText, targetMenu, modsMenu, startButton, indepCheck, resetCheck;
	var subpatEdit;
	var plugList;
	var baseEvent, player, playWatcher, playError, stream, trigResp, trigID;

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			var keys = IdentityDictionary.new;
			[
				#[freq, midinote, note, degree],
				#[delta, dur],
				#[amp, db]
			].do { |row|
				row.do { |item, i|
					keys.put(item, row[i..]);
				};
			};
			Library.put(this, \parentKeys, keys);

			ServerBoot.add {
				// t_trig is the outgoing trig: used when this pattern runs under its own power
				// inBus0 can supply a trig, to run this pattern by an external trigger
				SynthDef('HrPolyPattern', { |t_trig, inBus0, outBus0, trigID|
					var inTrig = InFeedback.ar(inBus0, 1),
					time = Timer.ar(inTrig);
					SendReply.ar(inTrig, '/HrPatternTrig', time, trigID);
					Out.ar(outBus0, K2A.ar(t_trig) ! 2);
				}).add;
			};
		};
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, this.name.asString, argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 600).rand, (Window.screenBounds.width - 450).rand, 600, 450), 1, 2, argCanvasXY).init
	}

	init {
		var width = (window.bounds.width - 16) * 0.25,
		boundFunc = { |i|
			Rect(2 + (i * (4 + width)), 2, width, 20)
		},
		makeCheckFunc = { |key|
			var black = Color.black;
			{ |view|
				var width = view.bounds.width, height = view.bounds.height;
				Pen.color_(black)
				.addRect(Rect(0, 0, width-1, height-1))
				.stroke;
				if(streamCtl[key] ? false) {
					Pen.width_(2)
					.moveTo(Point(0.25 * width, 0.25 * height))
					.lineTo(Point(0.75 * width, 0.75 * height))
					.moveTo(Point(0.75 * width, 0.25 * height))
					.lineTo(Point(0.25 * width, 0.75 * height))
					.stroke;
				}
			}
		};

		HrPMod.addDependant(this);  // track +/- mod targets
		baseEvent = Event(proto: (group: group));  // will use proto for play parameters
		streamCtl = (independent: true, reset: false);
		trigID = UniqueID.next;
		trigResp = OSCresponderNode(Server.default.addr, '/HrPatternTrig', { |time, resp, msg|
			if(msg[2] == trigID) {
				if(stream.isNil) {
					stream = this.asPattern.asStream;
				};
				stream.next(baseEvent).play;
			};
		}).add;

		if(extraArgs.size >= 1) {
			key = extraArgs[0].asSymbol;
			if(HrPbindef.exists(key).not) {
				HrPbindef(key, \dur, 1);
				iMadePdefs = iMadePdefs.add(key);
			}
		};

		HrPbindef.addDependant(this);  // track add/remove of HrPbindefs
		pdefMenu = PopUpMenu(window, boundFunc.(0))
		.items_(["empty"] ++ HrPbindef.keys.asArray.sort)
		.value_(0)
		.action_({ |view|
			this.key = view.item.asSymbol;
		});

		newText = TextField(window, boundFunc.(1))
		.string_("(name new)")
		.action_({ |view|
			var new = view.string.asSymbol;
			if(HrPbindef.exists(new).not) {
				HrPbindef(new, \dur, 1);
				iMadePdefs = iMadePdefs.add(new);
			};
			this.key = new;  // switch anyway
		});

		this.setPlugList;
		targetMenu = PopUpMenu(window, boundFunc.(2))
		.items_(["None"] ++ plugList.collect { |plug| plug.boundCanvasItem.string })
		.value_(0);

		modsMenu = PopUpMenu(window, boundFunc.(3))
		.items_(["None"]);

		this.initAction;

		startButton = Button(window, Rect(2, 24, width, 20))
		.states_([
			["stopped", Color.black, Color(1.0, 0.8, 0.8)],
			["running", Color.black, Color(0.8, 1.0, 0.8)]
		])
		.action_({ |view|
			this.run(view.value > 0);
		});

		indepCheck = UserView(window, Rect(startButton.bounds.right + 2, 24, 20, 20))
		.drawFunc_(makeCheckFunc.value(\independent))
		.mouseUpAction_({ |view, x, y|
			if(view.bounds.moveTo(0, 0).containsPoint(Point(x, y))) {
				streamCtl[\independent] = streamCtl[\independent].not;
				view.refresh;
			};
		});
		StaticText(window, Rect(indepCheck.bounds.right + 2, 24, 120, 20))
		.string_("separate stream");

		resetCheck = UserView(window, Rect(targetMenu.bounds.left, 24, 20, 20))
		.drawFunc_(makeCheckFunc.value(\reset))
		.mouseUpAction_({ |view, x, y|
			if(view.bounds.moveTo(0, 0).containsPoint(Point(x, y))) {
				streamCtl[\reset] = streamCtl[\reset].not;
				view.refresh;
			};
		});
		StaticText(window, Rect(resetCheck.bounds.right + 2, 24, 120, 20))
		.string_("reset");

		subpatEdit = HrPbindefEditor(window, Rect(2, 46,
			window.bounds.width - 4, window.bounds.height - 48));
		subpatEdit.addDependant(this);
		if(key.notNil) {
			this.key = key
		};

		saveGets = [
			{ key },
			{
				Array.fill(subpatEdit.size, { |i|
					[subpatEdit[i].key, subpatEdit[i].text]
				})
			},
			{ targetPlugin.tryPerform(\uniqueID) },
			{ startButton.value },
			{	var pmod;
				mappedMods.keys.reject(#[startOrStop, start].includes(_)).collect { |pmodname|
					pmod = HrPMod(pmodname);
					[pmodname, pmod.value, pmod.spec]
				}
			},
			{
				if(key.notNil) { HrPbindef(key).quant } { nil }
			},
			{ streamCtl }
		];
		saveSets = [
			{ |argg| key = argg },   // do not try to change gui here!
			// key was saved for HrPbindef references here:
			{ |argg|
				var pairs = Array(argg.size * 2), savedTexts = Array(argg.size);
				argg.do { |row|
					savedTexts.add(row[1]);
					pairs.add(row[0]).add(row[1].interpret);
				};
				HrPbindef(key).clear;
				HrPbindef(key, *pairs);
				this.key_(key, savedTexts);
			},
			{ |argg|
				this.targetPlugin = parentApp.alivePlugs.detect { |plug|
					plug.uniqueID == argg
				}
			},
			{ |argg| startButton.value = argg },
			{ |argg|
				// need to ensure all HrPMods exist
				argg.do { |row| HrPMod(*row) }
			},
			{ |argg|
				if(key.notNil and: { argg.notNil }) { HrPbindef(key).quant = argg };
			},
			{ |argg|
				streamCtl = argg ?? { streamCtl };
				indepCheck.refresh; resetCheck.refresh;
				startButton.doAction;
			}
		];

		modGets.put(\startOrStop, { startButton.value });
		modSets.put(\startOrStop, { |argg| defer { startButton.valueAction = binaryValue(argg > 0) } });
		// normally just "value = " but here we need a client action
		modMapSets.put(\startOrStop, { |argg| defer { startButton.valueAction = binaryValue(argg > 0) } });

		// this variant ignores "stop" modulator values (i.e. <= 0)
		// useful for finite-pattern gestures that should run to completion
		// no matter what the modulator does after triggering
		modGets.put(\start, { startButton.value });
		modSets.put(\start, { |argg|
			if(argg > 0) {
				defer { startButton.valueAction = 1 }
			};
		});
		modMapSets.put(\start, { |argg|
			if(argg > 0) {
				defer { startButton.valueAction = 1 }
			};
		});

		modGets.putAll(HrPMod.modGets);
		modSets.putAll(HrPMod.modSets);
		modMapSets.putAll(HrPMod.modMapSets);

		this.makeSynth;
	}

	key_ { |newKey, savedTexts|
		var temp;
		if(HrPbindef.exists(newKey)) {
			key = newKey;
			defer {
				subpatEdit.key_(key, savedTexts);
				// key may never be nil here, b/c of HrPbindef.exists check
				// remove 'empty' once a valid pattern has been assigned
				if(pdefMenu.items[0] == "empty") {
					temp = pdefMenu.items.copy;
					temp.removeAt(0);
					pdefMenu.items = temp;
				};
				pdefMenu.value = pdefMenu.items.detectIndex { |name| (name.asSymbol == key) } ? 0;
			};
			// playing stuff
		} {
			parentApp.displayStatus("% does not exist as a pattern. Use the text box at right to make a new one.".format(newKey), -1);
			defer {
				pdefMenu.value = pdefMenu.items.detectIndex { |name| (name.asSymbol == key) } ? 0;
			};
		}
	}

	targetPlugin_ { |plug|
		if((plug.tryPerform(\polySupport) ? false) or: { plug.isNil }) {
			targetPlugin.removeDependant(this);
			targetPlugin.tryPerform(\setPolyMode, false, this);
			targetPlugin = plug;
			if(plug.notNil) {
				targetPlugin.setPolyMode(true, this);
				baseEvent.proto[\instrument] = plug.defName.asSymbol;
				targetPlugin.addDependant(this);
			};
			defer {
				targetMenu.value = (plugList.indexOf(targetPlugin) ? -1) + 1;
				modsMenu.items = ["None"] ++ if(plug.notNil) { targetPlugin.modSets.keys.asArray.sort } { [] };
				if(subpatEdit.focusedRow.notNil) {
					// this will set modsMenu's value
					this.update(subpatEdit, \focusedRow, subpatEdit.focusedRow);
				};
			}
		} {
			parentApp.displayStatus("Plugin % does not support polyphonic use.".format(plug.boundCanvasItem.string), -1);
		}
	}

	notifyPlugAdd { |plug|
		var oldPlug, oldPlugList;
		oldPlugList = plugList;
		this.setPlugList;
		if(plugList != oldPlugList) {
			defer {
				oldPlug = oldPlugList[targetMenu.value - 1];
				targetMenu.items = ["None"] ++ plugList.collect { |plug|
					plug.boundCanvasItem.string
				};
				targetMenu.value = plugList.indexOf(oldPlug) ? 0;
			};
		};
	}

	notifyPlugKill { |plug|
		if(plug === targetPlugin) {
			this.targetPlugin = nil;
		};
		this.notifyPlugAdd(plug);
	}

	update { |obj, what ... more|
		var temp;
		case
		{ obj === subpatEdit } {
			switch(what)
			{ \focusedRow } {
				if(more[0].notNil) {
					temp = subpatEdit[more[0]].key.asSymbol;
					modsMenu.value = modsMenu.items.detectIndex({ |item|
						item.asSymbol == temp
					}) ? 0;
				};
			}
			{ \addRow } {
				subpatEdit[more[0]].focus(true);
			}
			{ \deleteRow } {
				subpatEdit[min(more[0], subpatEdit.size-1)].tryPerform(\focus, true);
			}
		}
		{ obj === targetPlugin and: { what == \badValue } } {
			this.run(false);
		}
		{ obj === HrPbindef and: { #[added, removed].includes(what) } } {
			defer {
				pdefMenu.items = (if(key.isNil) { ["empty"] } { [] })
				++ HrPbindef.keys.asArray.sort;
				pdefMenu.value = pdefMenu.items.detectIndex { |name| (name.asSymbol == key) } ? 0;
			}
		}
		{ obj === HrPMod } {
			switch(what)
			{ \added } {
				modGets.put(more[0], HrPMod.modGets[more[0]]);
				modSets.put(more[0], HrPMod.modSets[more[0]]);
				modMapSets.put(more[0], HrPMod.modMapSets[more[0]]);
				parentApp.alivePlugs.do(_.updateModTargets);
			}
			{ \removed } {
				modGets.removeAt(more[0]);
				modSets.removeAt(more[0]);
				modMapSets.removeAt(more[0]);
				parentApp.alivePlugs.do(_.updateModTargets);
			}
		}
	}

	/****** PLAY SUPPORT ******/

	run { |bool(false)|
		var wasPlaying = player.notNil;
		// don't do anything if state didn't change
		// also must have an existing pattern and target
		if(wasPlaying != bool) {
			if(bool) {
				if(HrPbindef.exists(key).not) {
					playError = \noPattern;
					defer {
						parentApp.displayStatus("No pattern to play", -1);
						startButton.value = 0
					};
					^this
				};
				if(this.targetIsEmpty) {
					playError = \noTarget;
					defer {
						parentApp.displayStatus("Set a target before playing", -1);
						startButton.value = 0
					};
					^this
				};
				playError = nil;
				player = this.asPattern.play(protoEvent: baseEvent, quant: HrPbindef(key).quant);
				playWatcher = SimpleController(player).put(\stopped, {
					playWatcher.remove;
					player = nil;
					// this.releaseSynth;
					defer { startButton.value = 0 };
				});
				defer { startButton.value = 1 };
				// this.makeSynth;
			} {
				player.stop;
				player = nil;
				// this.releaseSynth;
				defer { startButton.value = 0 };
			};
		};
	}

	asPattern {
		var keycheck = Library.at(HrPolyPattern, \parentKeys), keylist;
		^Pchain(
			// Here: interesting.
			// If the pattern didn't provide a syntharg,
			// we have to get it from the plugin.
			// BUT the pattern might provide, e.g., \degree
			// and this should override \freq in the plugin.
			Pfunc({ |ev|
				if(ev[\isRest] != true and: {
					ev.use { ~detunedFreqs.value.isRest.not }
				}) {
					(type: \set, id: synthInstance.nodeID, t_trig: 1, args: #[t_trig]).play;
				};
				targetPlugin.synthArgs.pairsDo { |key, value|
					if((keylist = keycheck[key]).notNil) {
						// this will also check 'key' itself
						if(keylist.every { |test| ev[test].isNil }) {
							// synthArgs might include an array arg
							// that should not multichannel expand in an event
							ev.put(key, if(value.isArray) { [value] } { value })
						}
					} {
						if(ev[key].isNil) {
							ev.put(key, if(value.isArray) { [value] } { value })
						};
					};
				};
				ev
			}),
			if(streamCtl[\independent]) {
				HrPbindef(key).source
			} {
				HrPbindef(key).initStream(streamCtl[\reset]).stream
			}
		)
	}

	// we don't know how many synths are here
	// setting the group touches all of them
	updateBusConnections { group.set(\outBus0, outBusses[0]) }
	cleanUp {
		HrPbindef.removeDependant(this);
		HrPMod.removeDependant(this);
		subpatEdit.removeDependant(this);
		if(targetPlugin.notNil) {
			targetPlugin.removeDependant(this);
			targetPlugin.setPolyMode(false, this);
		};
		playWatcher.remove;
		trigResp.remove;
		player.stop;
		iMadePdefs.do { |name| HrPbindef(name).remove };
	}

	mapModCtl { |paramName, ctlBus|
		if(#[startOrStop, start].includes(paramName)) {
			// adds/removes from mappedMods
			// not needed for synth, but needed for save/load
			super.mapModCtl(paramName, ctlBus);
		};
	}
	getMapModArgs { ^[] }

	makeSynth {
		synthInstance = Synth('HrPolyPattern', this.synthArgs, group);
	}

	releaseSynth {
		Server.default.makeBundle(Server.default.latency, {
			super.releaseSynth;
		});
	}

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], trigID: trigID]
	}


	/****** SUBCLASS SUPPORT ******/

	initAction {
		targetMenu.action_({ |view|
			this.targetPlugin = plugList[view.value-1];  // nil if "none"
		});

		modsMenu.action_({ |view|
			if(subpatEdit.focusedRow.notNil) {
				subpatEdit[subpatEdit.focusedRow].key = view.item.asSymbol;
			}
		});
	}

	setPlugList {
		plugList = parentApp.alivePlugs.select(_.polySupport);
	}

	targetIsEmpty { ^targetPlugin.isNil }
}