HrPolyPattern : HadronPlugin {
	var <key, <targetPlugin, iMadePdefs;
	var pdefMenu, newText, targetMenu, modsMenu, startButton;
	var subpatEdit;
	var plugList;
	var baseEvent, player, playWatcher, playError;

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
		}
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrPolyPattern", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 600).rand, (Window.screenBounds.width - 450).rand, 600, 450), 0, 2, argCanvasXY).init
	}

	init {
		var width = (window.bounds.width - 20) * 0.2,
		boundFunc = { |i|
			Rect(2 + (i * (4 + width)), 2, width, 20)
		};

		HrPMod.addDependant(this);  // track +/- mod targets
		baseEvent = Event(proto: (group: group));  // will use proto for play parameters

		if(extraArgs.size >= 1) {
			key = extraArgs[0].asSymbol;
			if(HrPbindef.exists(key).not) {
				HrPbindef(key, \dur, 1);
				iMadePdefs = iMadePdefs.add(key);
			}
		};

		startButton = Button(window, Rect(2, 2, width, 20))
		.states_([
			["stopped", Color.black, Color(1.0, 0.8, 0.8)],
			["running", Color.black, Color(0.8, 1.0, 0.8)]
		])
		.action_({ |view|
			this.run(view.value > 0);
		});

		HrPbindef.addDependant(this);  // track add/remove of HrPbindefs
		pdefMenu = PopUpMenu(window, boundFunc.(1))
		.items_(["empty"] ++ HrPbindef.keys.asArray.sort)
		.value_(0)
		.action_({ |view|
			this.key = view.item.asSymbol;
		});

		newText = TextField(window, boundFunc.(2))
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
		targetMenu = PopUpMenu(window, boundFunc.(3))
		.items_(["None"] ++ plugList.collect { |plug| plug.boundCanvasItem.string })
		.value_(0);

		modsMenu = PopUpMenu(window, boundFunc.(4))
		.items_(["None"]);

		this.initAction;

		subpatEdit = HrPbindefEditor(window, Rect(2, 24,
			window.bounds.width - 4, window.bounds.height - 26));
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
			{ targetPlugin.uniqueID },
			{ startButton.value },
			{	var pmod;
				mappedMods.keys.reject(_ == \run).collect { |pmodname|
					pmod = HrPMod(pmodname);
					[pmodname, pmod.value, pmod.spec]
				}
			},
			{
				if(key.notNil) { HrPbindef(key).quant } { nil }
			}
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
			{ |argg| startButton.valueAction = argg },
			{ |argg|
				// need to ensure all HrPMods exist
				argg.do { |row| HrPMod(*row) }
			},
			{ |argg|
				if(key.notNil and: { argg.notNil }) { HrPbindef(key).quant = argg };
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
			targetPlugin.tryPerform(\polyMode_, false);
			targetPlugin = plug;
			if(plug.notNil) {
				targetPlugin.polyMode = true;
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
					defer { startButton.value = 0 };
				});
			} {
				player.stop;
				player = nil;
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
				targetPlugin.synthArgs.pairsDo { |key, value|
					if((keylist = keycheck[key]).notNil) {
						// this will also check 'key' itself
						if(keylist.every { |test| ev[test].isNil }) {
							ev.put(key, value)
						}
					} {
						if(ev[key].isNil) { ev.put(key, value) };
					};
				};
				ev
			}),
			HrPbindef(key).source
		)
	}

	// we don't know how many synths are here
	// setting the group touches all of them
	updateBusConnections { group.set(\outBus0, outBusses[0]) }
	cleanUp {
		HrPbindef.removeDependant(this);
		HrPMod.removeDependant(this);
		subpatEdit.removeDependant(this);
		if(targetPlugin.notNil) { targetPlugin.removeDependant(this) };
		playWatcher.remove;
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