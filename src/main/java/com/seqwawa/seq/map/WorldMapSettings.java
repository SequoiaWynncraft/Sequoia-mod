package com.seqwawa.seq.map;

import java.util.EnumMap;
import java.util.Set;
import java.util.TreeSet;

public final class WorldMapSettings {
    private static final WorldMapSettings INSTANCE = new WorldMapSettings();

    private final EnumMap<GatheringProfession, Boolean> professionToggles = new EnumMap<>(GatheringProfession.class);
    private final EnumMap<WorldMapSidebarPanel, Boolean> sidebarPanels = new EnumMap<>(WorldMapSidebarPanel.class);
    private final Set<String> resourceFilters = new TreeSet<>();
    private int clusterEps = GatheringNodeClusterer.DEFAULT_EPS;
    private int clusterMinSamples = GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
    private boolean showClusters = true;
    private boolean gatheringTotemSolverEnabled;
    private boolean showGatheringTotemHulls = true;
    private boolean showGatheringTotemPlayerRadius = true;
    private boolean showGatheringTotemNodeReach = true;
    private boolean showGatheringTotemCoveredNodes = true;
    private boolean showOtherOptimalGatheringTotems = true;
    private boolean showTerritories;
    private boolean showTerritoryNames;
    private boolean showDebugInfo;
    private ClusterScoreMode clusterScoreMode = ClusterScoreMode.FOUR_TICK;
    private GatheringAnalysisScope gatheringAnalysisScope = GatheringAnalysisScope.ALL;
    private GatheringTotemSearchTarget gatheringTotemSearchTarget = GatheringTotemSearchTarget.ALL_FILTERED;
    private MapDisplayMode displayMode = MapDisplayMode.GATHERING;
    private WorldEventDisplayFilter worldEventDisplayFilter = WorldEventDisplayFilter.ALL;
    private boolean insightsSidebarOpen = true;
    private String selectedTerritoryName;
    private long version;

    private WorldMapSettings() {
        for (GatheringProfession profession : GatheringProfession.values()) {
            professionToggles.put(profession, true);
        }
        for (WorldMapSidebarPanel panel : WorldMapSidebarPanel.values()) {
            sidebarPanels.put(panel, true);
        }
    }

    public static WorldMapSettings getInstance() {
        return INSTANCE;
    }

    public synchronized int clusterEps() {
        return clusterEps;
    }

    public synchronized int clusterMinSamples() {
        return clusterMinSamples;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized Set<String> resourceFilters() {
        return new TreeSet<>(resourceFilters);
    }

    public synchronized void setResourceFilters(Set<String> resourceFilters) {
        this.resourceFilters.clear();
        if (resourceFilters != null) {
            this.resourceFilters.addAll(resourceFilters);
        }
    }

    public synchronized EnumMap<GatheringProfession, Boolean> professionToggles() {
        return new EnumMap<>(professionToggles);
    }

    public synchronized void setProfessionEnabled(GatheringProfession profession, boolean enabled) {
        professionToggles.put(profession, enabled);
    }

    public synchronized boolean showClusters() {
        return showClusters;
    }

    public synchronized void setShowClusters(boolean showClusters) {
        this.showClusters = showClusters;
    }

    public synchronized boolean gatheringTotemSolverEnabled() {
        return gatheringTotemSolverEnabled;
    }

    public synchronized void setGatheringTotemSolverEnabled(boolean gatheringTotemSolverEnabled) {
        this.gatheringTotemSolverEnabled = gatheringTotemSolverEnabled;
    }

    public synchronized GatheringTotemSearchTarget gatheringTotemSearchTarget() {
        return gatheringTotemSearchTarget;
    }

    public synchronized void setGatheringTotemSearchTarget(GatheringTotemSearchTarget gatheringTotemSearchTarget) {
        this.gatheringTotemSearchTarget = gatheringTotemSearchTarget == null
                ? GatheringTotemSearchTarget.ALL_FILTERED
                : gatheringTotemSearchTarget;
    }

    public synchronized boolean showGatheringTotemHulls() {
        return showGatheringTotemHulls;
    }

    public synchronized void setShowGatheringTotemHulls(boolean showGatheringTotemHulls) {
        this.showGatheringTotemHulls = showGatheringTotemHulls;
    }

    public synchronized boolean showGatheringTotemPlayerRadius() {
        return showGatheringTotemPlayerRadius;
    }

    public synchronized void setShowGatheringTotemPlayerRadius(boolean showGatheringTotemPlayerRadius) {
        this.showGatheringTotemPlayerRadius = showGatheringTotemPlayerRadius;
    }

    public synchronized boolean showGatheringTotemNodeReach() {
        return showGatheringTotemNodeReach;
    }

    public synchronized void setShowGatheringTotemNodeReach(boolean showGatheringTotemNodeReach) {
        this.showGatheringTotemNodeReach = showGatheringTotemNodeReach;
    }

    public synchronized boolean showGatheringTotemCoveredNodes() {
        return showGatheringTotemCoveredNodes;
    }

    public synchronized void setShowGatheringTotemCoveredNodes(boolean showGatheringTotemCoveredNodes) {
        this.showGatheringTotemCoveredNodes = showGatheringTotemCoveredNodes;
    }

    public synchronized boolean showOtherOptimalGatheringTotems() {
        return showOtherOptimalGatheringTotems;
    }

    public synchronized void setShowOtherOptimalGatheringTotems(boolean showOtherOptimalGatheringTotems) {
        this.showOtherOptimalGatheringTotems = showOtherOptimalGatheringTotems;
    }

    public synchronized boolean showTerritories() {
        return showTerritories;
    }

    public synchronized void setShowTerritories(boolean showTerritories) {
        this.showTerritories = showTerritories;
    }

    public synchronized boolean showTerritoryNames() {
        return showTerritoryNames;
    }

    public synchronized void setShowTerritoryNames(boolean showTerritoryNames) {
        this.showTerritoryNames = showTerritoryNames;
    }

    public synchronized boolean showDebugInfo() {
        return showDebugInfo;
    }

    public synchronized boolean toggleDebugInfo() {
        showDebugInfo = !showDebugInfo;
        return showDebugInfo;
    }

    public synchronized ClusterScoreMode clusterScoreMode() {
        return clusterScoreMode;
    }

    public synchronized void setClusterScoreMode(ClusterScoreMode clusterScoreMode) {
        this.clusterScoreMode = clusterScoreMode == null ? ClusterScoreMode.FOUR_TICK : clusterScoreMode;
    }

    public synchronized GatheringAnalysisScope gatheringAnalysisScope() {
        return gatheringAnalysisScope;
    }

    public synchronized MapDisplayMode displayMode() {
        return displayMode;
    }

    public synchronized void setDisplayMode(MapDisplayMode displayMode) {
        this.displayMode = displayMode == null ? MapDisplayMode.GATHERING : displayMode;
    }

    public synchronized WorldEventDisplayFilter worldEventDisplayFilter() {
        return worldEventDisplayFilter;
    }

    public synchronized void setWorldEventDisplayFilter(WorldEventDisplayFilter worldEventDisplayFilter) {
        this.worldEventDisplayFilter = worldEventDisplayFilter == null
                ? WorldEventDisplayFilter.ALL
                : worldEventDisplayFilter;
    }

    public synchronized boolean insightsSidebarOpen() {
        return insightsSidebarOpen;
    }

    public synchronized void setInsightsSidebarOpen(boolean insightsSidebarOpen) {
        this.insightsSidebarOpen = insightsSidebarOpen;
    }

    public synchronized boolean sidebarPanelExpanded(WorldMapSidebarPanel panel) {
        return sidebarPanels.getOrDefault(panel, true);
    }

    public synchronized void setSidebarPanelExpanded(WorldMapSidebarPanel panel, boolean expanded) {
        if (panel != null) {
            sidebarPanels.put(panel, expanded);
        }
    }

    public synchronized void setGatheringAnalysisScope(GatheringAnalysisScope gatheringAnalysisScope) {
        this.gatheringAnalysisScope = gatheringAnalysisScope == null ? GatheringAnalysisScope.ALL : gatheringAnalysisScope;
    }

    public synchronized String selectedTerritoryName() {
        return selectedTerritoryName;
    }

    public synchronized void setSelectedTerritoryName(String selectedTerritoryName) {
        String normalized = selectedTerritoryName == null ? null : selectedTerritoryName.trim();
        this.selectedTerritoryName = normalized == null || normalized.isEmpty() ? null : normalized;
        if (this.selectedTerritoryName == null && gatheringAnalysisScope == GatheringAnalysisScope.SELECTED_TERRITORY) {
            gatheringAnalysisScope = GatheringAnalysisScope.ALL;
        }
    }

    public synchronized void setClusterEps(int clusterEps) {
        if (this.clusterEps == clusterEps) {
            return;
        }
        this.clusterEps = clusterEps;
        version++;
        GatheringClusterCache.getInstance().clear();
    }

    public synchronized void setClusterMinSamples(int clusterMinSamples) {
        if (this.clusterMinSamples == clusterMinSamples) {
            return;
        }
        this.clusterMinSamples = clusterMinSamples;
        version++;
        GatheringClusterCache.getInstance().clear();
    }

    public synchronized void resetClusterParams() {
        boolean changed = clusterEps != GatheringNodeClusterer.DEFAULT_EPS
                || clusterMinSamples != GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
        clusterEps = GatheringNodeClusterer.DEFAULT_EPS;
        clusterMinSamples = GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
        if (changed) {
            version++;
            GatheringClusterCache.getInstance().clear();
        }
    }

    public synchronized String describe() {
        return "eps=" + clusterEps + ", minSamples=" + clusterMinSamples;
    }
}
