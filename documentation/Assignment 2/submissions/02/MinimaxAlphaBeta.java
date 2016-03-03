package edu.cwru.sepia.agent.minimax;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;

public class MinimaxAlphaBeta extends Agent {
	private static final long serialVersionUID = 1L;
	private final int numPlys;

    public MinimaxAlphaBeta(int playernum, String[] args)
    {
        super(playernum);

        if(args.length < 1)
        {
            System.err.println("You must specify the number of plys");
            System.exit(1);
        }

        numPlys = Integer.parseInt(args[0]);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        GameStateChild bestChild = alphaBetaSearch(new GameStateChild(newstate),
                numPlys,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY);

        return bestChild.action;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {

    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * Search with Alpha Beta pruning
     *
     * @param node The action and state to search from
     * @param depth The remaining number of plys under this node
     * @param alpha The current best value for the maximizing node from this node to the root
     * @param beta The current best value for the minimizing node from this node to the root
     * @return The best child of this node with updated values
     */
    public GameStateChild alphaBetaSearch(GameStateChild node, int depth, double alpha, double beta){
        double value = maxValue(node, depth, alpha, beta);
        return getStateWithValue(node, value);
    }

	private double maxValue(GameStateChild node, int depth, double alpha, double beta){
    	if(cutOffTest(node, depth)){
    		return node.state.getUtility();
    	}
    	double value = Double.NEGATIVE_INFINITY;
		for(GameStateChild child : orderChildrenWithHeuristics(node.state.getChildren())){
    		value = Math.max(value, minValue(child, depth - 1, alpha, beta));
    		if(value >= beta){
    			return value;
    		}
    		alpha = Math.max(alpha, value);
    	}
    	return value;
    }
    
	private double minValue(GameStateChild node, int depth, double alpha, double beta) {
		if(cutOffTest(node, depth)){
			return node.state.getUtility();
		}
		double value = Double.POSITIVE_INFINITY; 
		for(GameStateChild child : orderChildrenWithHeuristics(node.state.getChildren())){
			value = Math.min(value, maxValue(child, depth - 1, alpha, beta));
			if(value <= alpha){
				return value;
			}
			beta = Math.min(beta, value);
		}
		return value;
	}	

	/**
	 * Determines when to end the recursion
	 * 
	 * @param node
	 * @param depth
	 * @return true if the depth limit has been reached or the current node is a terminal node
	 */
	private boolean cutOffTest(GameStateChild node, int depth) {
		return depth == 0;
	}

	/**
	 * Heuristic: a footman is better off if attacking so if both footmen are attacking that
	 * state is first in the list ones in which only one footman is attacking are next
	 * and the states where footmen are simply moving are last
	 * 
	 * Initially an idea was to expand the nodes that move the footman towards an archer first
	 * and other moves later however I ran into a problem with that when obstacles were involved
	 * and had better luck with just using attacks as the heuristic.
	 * 
	 * @param children list of possible next GameStateChild
	 * @return list of GameStateChild in order by which should be expanded first by alpha beta search
	 */
    public List<GameStateChild> orderChildrenWithHeuristics(List<GameStateChild> children){ 
        List<GameStateChild> ordered = new LinkedList<GameStateChild>();
        for(GameStateChild child : children){
        	int count = 0;
        	for(Action action : child.action.values()){
        		if(action.getType().name().equals(GameState.ACTION_ATTACK_NAME)){
        			count++;
        		}
        	}
        	if(count == child.action.size()){
        		ordered.add(0, child);
        	} else if (count > 0){
        		if(ordered.isEmpty()){
        			ordered.add(0, child);
        		} else {
        			ordered.add(1, child);
        		}
        	} else {
        		ordered.add(child);
        	}
        }
        return ordered;
    }
    
    /**
     *  
     * @param node the starting node of ABSearch
     * @param value value found in ABSearch
     * @return the node that matches the values provided by alpha beta search if no match returns the highest utility state
     */
    private GameStateChild getStateWithValue(GameStateChild node, double value) {
    	List<GameStateChild> children = node.state.getChildren();
    	for(GameStateChild child : children){
    		if(child.state.getUtility() == value){
    			return child;
    		}
    	}
    	// For some reason the value returned by AB search doesn't match any of the child state's utilities
        children.sort((o1, o2) -> {
        	if(o1.state.getUtility() > o2.state.getUtility()){
        		return -1;
        	} else if (o1.state.getUtility() < o2.state.getUtility()){
        		return 1;
        	} else {
        		return 0;
        	}
        });
        if(children.isEmpty()){
        	// Something went very wrong elsewhere if no children where found at all
        	// I guess just don't do anything
        	return node;
        }
    	return children.get(0);
	}
}
