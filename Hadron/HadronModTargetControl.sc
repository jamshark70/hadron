HadronModTargetControl
{
	var parentApp, <currentSelPlugin, <currentSelParam, myView,
	targetAppMenu, targetParamMenu, loadHolder;
	
	*new
	{|argParentView, argBounds, argParentApp|
	
		^super.new.init(argParentView, argBounds, argParentApp);
	}
	
	init
	{|argParentView, argBounds, argParentApp|
	
		parentApp = argParentApp;
		myView = CompositeView(argParentView, Rect(argBounds.left, argBounds.top, argBounds.width, 20));
		
		targetAppMenu = PopUpMenu(myView, Rect(0, 0, (argBounds.width/2)-5, 20))
		.action_
		({|menu|
			var oldplug = currentSelPlugin;
			if(menu.value == 0,
			{
				currentSelPlugin = nil;
				currentSelParam = nil;
				targetParamMenu.items = ["Nothing."];
				targetParamMenu.value = 0;
			},
			{
				currentSelPlugin = parentApp.alivePlugs[menu.value - 1];
				currentSelParam = nil;
				targetParamMenu.items = ["Nothing"] ++ currentSelPlugin.modSets.keys.asArray;
				targetParamMenu.value = 0;
			});
			this.changed(\currentSelPlugin, currentSelPlugin, oldplug, currentSelParam);
		});
		
		this.refreshAppMenu;
		
		targetParamMenu = PopUpMenu(myView, Rect((argBounds.width/2)+5, 0, (argBounds.width/2)-5, 20))
		.items_(["Nothing."])
		.action_
		({|menu|
			var oldparam = currentSelParam;
			if(menu.value == 0,
			{
				currentSelParam = nil;
			},
			{
				currentSelParam = menu.item.asSymbol;
			});
			this.changed(\currentSelParam, currentSelParam, currentSelPlugin, oldparam);
		});
		
	}
	
	plugAdded
	{
		this.refreshAppMenu;
	}
	
	plugRemoved
	{|argPlug|
	
		var plugIndex;
		
		if(currentSelPlugin === argPlug,
		{
			this.refreshAppMenu(argPlug);
			currentSelPlugin = nil;
			currentSelParam = nil;
			targetParamMenu.items = ["Nothing."];
			targetParamMenu.value = 0;
			targetAppMenu.value = 0;
		},
		{
			plugIndex = parentApp.alivePlugs.indexOf(argPlug) + 1; //+1 because menu has an extra "Nothing" entry.
			if(targetAppMenu.value < plugIndex,
			{
				this.refreshAppMenu(argPlug);
			},
			{
				this.refreshAppMenu(argPlug);
				targetAppMenu.value = targetAppMenu.value - 1;
			});
		});
	}
	
	refreshAppMenu
	{|argRejectPlug|
		var oldval = max(0, targetAppMenu.value ? 0);
		targetAppMenu.items_(["Nothing."] ++ parentApp.alivePlugs.reject({|item| item === argRejectPlug; })
			.collect({|item| item.class.asString + item.ident }))
			.value_(oldval);
	}
	
	modulateWithValue
	{|argNormalizedValue|
	
		if((currentSelPlugin != nil) and: { currentSelParam != nil },
		{
			currentSelPlugin.modSets.at(currentSelParam.asSymbol).value(argNormalizedValue);
		});
	}

	updateMappedGui { |argRealValue|
		if((currentSelPlugin != nil) and: { currentSelParam != nil }) {
			currentSelPlugin.modMapSets.at(currentSelParam.asSymbol).value(argRealValue);
		};
	}

	map { |ctlBus|
		var mapped;
		if(mapped = (currentSelPlugin != nil) and: { currentSelParam != nil },
		{
			currentSelPlugin.mapModCtl(currentSelParam, ctlBus);
		});
		^mapped
	}
	unmap { |oldplug(currentSelPlugin), oldparam(currentSelParam)|
		if((oldplug != nil) and: { oldparam != nil },
		{
			oldplug.mapModCtl(oldparam, -1);
		});
	}
	
	getSaveValues
	{
		 ^[targetAppMenu.value, targetParamMenu.value];
	}
	
	putSaveValues
	{|argValArray|
	
		loadHolder = argValArray;
	}
	
	doWakeFromLoad
	{
		targetAppMenu.valueAction_(loadHolder[0]);
		targetParamMenu.valueAction_(loadHolder[1]);
	}

}