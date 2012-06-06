HrMonoPattern : HrPolyPattern {
	var targetPlugins;  // for bad value check: all plugins referenced in HrMonoTargets

	init {
		targetPlugins = IdentitySet.new;
		super.init;
		baseEvent = Event(proto: (type: \hrMonoPattern));

		saveGets[1] = {
			Array.fill(subpatEdit.size, { |i|
				[subpatEdit[i].key.getSaveValues, subpatEdit[i].text]
			})
		};

		saveSets[1] = { |argg|
			var pairs = Array(argg.size * 2), savedTexts = Array(argg.size),
			target;
			argg.do { |row|
				savedTexts.add(row[1]);
				if(row[0].isString and: { row[0].beginsWith("HrMonoTarget") }) {
					target = "{ |app| % }".format(row[0].replace("##", "app"))
					.interpret.value(parentApp);
					if(target.respondsTo(\plugin)) {
						this.addTarget(target.plugin);
					};
				} {
					target = row[0];
				};
				pairs.add(target).add(row[1].interpret);
			};
			HrPbindef(key).clear;
			HrPbindef(key, *pairs);
			this.key_(key, savedTexts);
		};
		saveSets[2] = nil;
	}

	targetPlugin_ { |plug|
		var rowKey;
		targetPlugin = plug;
		defer {
			targetMenu.value = (plugList.indexOf(targetPlugin) ? -1) + 1;
			modsMenu.items = ["None"] ++ if(plug.notNil) { targetPlugin.modSets.keys.asArray.sort } { [] };
			if(subpatEdit.focusedRow.notNil) {
				rowKey = subpatEdit[subpatEdit.focusedRow].tryPerform(\key);
				if(rowKey.isKindOf(HrMonoTarget)) {
					modsMenu.value = modsMenu.items.indexOf(rowKey.modName) ? 0;
				};
			};
		}
	}

	// target/mods menus have different actions here
	initAction {
		targetMenu.action_({ |view|
			this.targetPlugin = plugList[view.value-1];  // nil if "none"
		});

		modsMenu.action_({ |view|
			if(subpatEdit.focusedRow.notNil) {
				if(view.value > 0) {
					subpatEdit[subpatEdit.focusedRow].key = HrMonoTarget(
						parentApp, targetPlugin, view.item.asSymbol
					);
					this.addTarget(targetPlugin);
				} {
					subpatEdit[subpatEdit.focusedRow].key = \none;
				};
			}
		});
	}

	// can always play... it might not *do* anything but you can play it
	targetIsEmpty { ^false }

	addTarget { |target|
		targetPlugins.add(target);
		target.addDependant(this);
	}

	removeTarget { |target|
		// cannot remove unless ALL HrMonoTargets for that plugin have been deleted
		subpatEdit.size.do { |i|
			if(subpatEdit[i].key.tryPerform(\plugin) === target) { ^this };
		};
		targetPlugins.remove(target);
		target.removeDependant(this);
	}

	update { |obj, what ... more|
		var temp;
		case
		{ obj === subpatEdit } {
			switch(what)
			{ \focusedRow } {
				if(more[0].notNil) {
					temp = subpatEdit[more[0]].key;
					if(temp.isKindOf(HrMonoTarget)) {
						this.targetPlugin = temp.plugin;
					} {
						this.targetPlugin = nil;
					};
				};
			}
			{ \addRow } {
				subpatEdit[more[0]].focus(true);
			}
			{ \deleteRow } {
				if(more[1].key.isKindOf(HrMonoTarget)) {
					this.removeTarget(more[1].key.plugin);
				};
				subpatEdit[min(more[0], subpatEdit.size-1)].tryPerform(\focus, true);
			}
			{ \rowKey } {
				// more[0] == index, more[1] == oldkey
				if(more[1].isKindOf(HrMonoTarget)) {
					this.removeTarget(more[1].plugin);
				};				
			}
		}
		{ what == \badValue and: { targetPlugins.includes(obj) } } {
			this.run(false);
		}
		// default case
		{ super.update(obj, what, *more) }
	}

	asPattern {
		^Pchain(
			Pfunc({ |ev|
				if(ev[\isRest] != true and: {
					ev.use { ~detunedFreqs.value.isRest.not }
				}) {
					(type: \set, id: synthInstance.nodeID, t_trig: 1, args: #[t_trig]).play;
				};
				ev
			}),
			HrPbindef(key).source
		)
	}

	setPlugList {
		plugList = parentApp.alivePlugs.reject(_ === this);
	}

	notifyPlugKill { |plug|
		this.notifyPlugAdd(plug);
		subpatEdit.size.do { |i|
			if(subpatEdit[i].key.tryPerform(\plugin) === plug) {
				subpatEdit[i].key = \none;
			};
		};
	}

	notifyIdentChanged { |plug, ident|
		if(key.notNil) {
			HrPbindef(key).source.pairs.pairsDo { |key, value, i|
				if(key.isKindOf(HrMonoTarget) and: { key.plugin === plug }) {
					key.setString;
					subpatEdit[i>>1].key = key;  // update gui
				};
			}
		};
	}

	cleanUp {
		targetPlugin = nil;
		targetPlugins.do(_.removeDependant(this));
		super.cleanUp;
	}

	*initClass {
		this.addHadronPlugin;
		StartUp.add {
			Event.addEventType(\hrMonoPattern, #{ |server|
				var freqs, lag, dur, strum, bndl, msgFunc;
				freqs = ~freq = ~detunedFreq.value;
				~sustain = ~sustain.value;

				if(~isRest != true and: { freqs.isRest.not }) {
					~server = server;
					freqs = ~freq;
					~amp = ~amp.value;

					bndl = server.makeBundle(false, {
						currentEnvironment.keysValuesDo { |key, val|
							if(key.isKindOf(HrMonoTarget)) {
								// this generates the n_set message to add to the bundle
								if(val.class === Symbol) {
									key.value = val.envirGet
								} {
									key.value = val;
								};
							};
						};
					});

					~schedBundleArray.value(~lag, ~timingOffset, server, bndl);
				};
			});
		};
	}
}


// represents a modulatable control
// and provides a human-readable string for the pattern editor
HrMonoTarget {
	var <parentApp, <plugin, <modName;
	var displayString, keySymbol;

	*new { |parentApp, plugin, modName|
		^super.newCopyArgs(parentApp, plugin, modName).setString;
	}
	parse { |symbol|
		var parms, id, plugin, name;
		parms = symbol.asString.split($:);
		id = parms[0].asInteger;
		name = parms[1].asSymbol;
		plugin = parentApp.alivePlugs.detect { |plug| plug.uniqueID == id };
		if(plugin.notNil and: { plugin.modSets[name].notNil }) {
			this.plugin_(plugin).modName_(name);
		} {
			Error("HrMonoTarget: Invalid load string %".format(symbol.asCompileString)).throw;
		};
	}

	value {
		^if(plugin.notNil) {
			plugin.modGets[modName].value
		};
	}

	value_ { |val|
		if(plugin.notNil) {
			plugin.modSets[modName].value(val);
		};
	}

	parentApp_ { |aHadron|
		parentApp = aHadron;
		// this is almost certain to be nil, but who knows?
		if(plugin.notNil) {
			plugin = parentApp.alivePlugs.detect { |plug| plug.uniqueID == plugin.uniqueID };
			if(plugin.notNil) {
				if(plugin.modSets.keys.includes(modName).not) {
					modName = nil;
				}
			};
		};
		this.changed(\parentApp, parentApp);
	}

	plugin_ { |aPlugin|
		plugin = aPlugin;
		if(plugin.notNil) {
			if(plugin.modSets.keys.includes(modName).not) {
				modName = nil;
			};
		};
		this.setString.changed(\plugin, plugin);
	}

	modName_ { |aSymbol|
		if(plugin.modSets.keys.includes(aSymbol).not) {
			modName = nil;
		} {
			modName = aSymbol;
		};
		this.setString.changed(\modName, modName);
	}

	setString {
		if(plugin.isNil) {
			displayString = "<empty>";
			keySymbol = '0:none';
		} {
			displayString = "/%:%".format(
				if(plugin.ident == "unnamed") { plugin.class.name } { plugin.ident },
				modName
			);
			keySymbol = "%:%".format(plugin.uniqueID, modName).asSymbol;
		};
	}

	// human-readable for interface
	// this will be called more often than it changes, hence caching in a var
	// NOT UNIQUE
	asString { ^displayString }

	// internal use, for event keys
	// unique, can be used to reconstruct
	asSymbol { ^keySymbol }

	asHrPbindefKey { ^this }

	getSaveValues {
		^"%(##).parse(%)".format(this.class.name, this.asSymbol.asCompileString)
	}

	== { |aTarget|
		^(aTarget.class === this.class and: {
			parentApp === aTarget.parentApp and: {
				plugin === aTarget.plugin and: {
					modName == aTarget.modName
				}
			}
		})
	}
}
