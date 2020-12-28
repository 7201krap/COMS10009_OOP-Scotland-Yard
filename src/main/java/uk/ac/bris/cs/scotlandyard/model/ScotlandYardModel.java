package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.SECRET;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import uk.ac.bris.cs.gamekit.graph.Graph;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {

	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private List<ScotlandYardPlayer> scotlandyardplayers = new ArrayList<> ();
	private Set<Spectator> spectators = new HashSet<> ();
	private int ROUND;
	private int playerindex;
	private int mrXlastloc;
	private int mrXcurrentloc;
	private Set<Colour> winners = new HashSet<> ();

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
		// TODO

			// Check if given arguments are valid.
			if(rounds.isEmpty()) throw new IllegalArgumentException("Empty rounds");
			this.rounds = requireNonNull(rounds);

			if(graph.isEmpty()) throw new IllegalArgumentException("Empty map");
			this.graph = requireNonNull(graph);

			if(mrX.colour != BLACK) throw new IllegalArgumentException("MrX should be Black");

			ArrayList<PlayerConfiguration> configurations = new ArrayList<> ();
			for(PlayerConfiguration configuration : restOfTheDetectives) {
				configurations.add(requireNonNull(configuration));
			}
			configurations.add(0, requireNonNull(firstDetective));
			configurations.add(0, requireNonNull(mrX));

			Set<Colour> colset = new HashSet<> ();
			for (PlayerConfiguration configuration : configurations) {
				if(colset.contains(configuration.colour)) {
					throw new IllegalArgumentException("Duplicate colour");
				}
				colset.add(configuration.colour);
			}

			Set<Integer> locset = new HashSet<> ();
			for (PlayerConfiguration configuration : configurations) {
				if(locset.contains(configuration.location)) {
					throw new IllegalArgumentException("Duplicate location");
				}
				locset.add(configuration.location);
			}

			for(PlayerConfiguration configuration : configurations) {
				if(!configuration.tickets.keySet().contains(Ticket.BUS) ||
					 !configuration.tickets.keySet().contains(Ticket.DOUBLE) ||
					 !configuration.tickets.keySet().contains(Ticket.SECRET) ||
					 !configuration.tickets.keySet().contains(Ticket.TAXI) ||
					 !configuration.tickets.keySet().contains(Ticket.UNDERGROUND)) {
						 throw new IllegalArgumentException("Players should have all ticket types");
					 }
			}

			for(int i=1; i < configurations.size(); i++) {
				if(configurations.get(i).tickets.get(Ticket.DOUBLE) != 0 ||
					 configurations.get(i).tickets.get(Ticket.SECRET) != 0) {
						 throw new IllegalArgumentException("Detectives have DOUBLE or SECRET ticket");
					 }
			}

			// Store information of PlayerConfiguration to ScotlandYardPlayer.
			for(PlayerConfiguration configuration : configurations) {
				this.scotlandyardplayers.add(new ScotlandYardPlayer(configuration.player,
				configuration.colour,	configuration.location, configuration.tickets));
			}

			// Initialise the rest of the fields.
			this.ROUND = NOT_STARTED;
			this.playerindex = 0;
			this.mrXlastloc = 0;
			this.mrXcurrentloc = 0;

	}

	// The method that gives out a ScotlandYardPlayer object according to a given Colour.
	private ScotlandYardPlayer getCurrentPlayerFromColour(Colour colour) {
		int index = this.getPlayers().indexOf(colour);
		return this.scotlandyardplayers.get(index);
	}

	// The method to check if a node is occupied by any detective.
	private boolean detectiveDetected(int destination) {
		boolean res = false;
		for (ScotlandYardPlayer player : this.scotlandyardplayers) {
			if(player.isDetective() && destination == player.location()) {
				res = true;
			}
		}
		return res;
	}

	// The method that gives out a set of valid single moves of a current player.
	private Set<Move> validFirstMove(ScotlandYardPlayer currentplayer, List<Edge<Integer, Transport>> edges) {
		Set<Move> firstmoves = new HashSet<> ();

		// Iterate through connected edges from a currentplayer's node.
		for(Edge<Integer, Transport> edge : edges) {
			Ticket ticket = Ticket.fromTransport(edge.data());
			int destination = edge.destination().value();

			// Check if the currentplayer has enough ticket and a destination is occupied.
			if(currentplayer.hasTickets(ticket) && !this.detectiveDetected(destination)) {
				firstmoves.add(new TicketMove(currentplayer.colour(), ticket, destination));
			}

			// Add SECRET moves for Mr.X's.
			if(currentplayer.hasTickets(Ticket.SECRET) && !this.detectiveDetected(destination)) {
				firstmoves.add(new TicketMove(currentplayer.colour(), Ticket.SECRET, destination));
			}
		}
		return firstmoves;
	}

	// The method that returns a set of valid double moves of Mr.X.
	private Set<Move> validDoubleMove(ScotlandYardPlayer currentplayer, Set<Move> firstmoves) {
		Set<Move> doublemoves = new HashSet<>();
		if(currentplayer.hasTickets(Ticket.DOUBLE) && this.ROUND != this.rounds.size() - 1) {

			Set<Move> secondmoves = new HashSet<> ();

			// Iterate through a set of valid single moves.
			for(Move fmv : firstmoves) {

				// Downcast to TicketMove.
				TicketMove firstmove = (TicketMove) fmv;

				// All possible second edges from a destination of a first move.
				List<Edge<Integer, Transport>> secondedges = new ArrayList<>();
				secondedges.addAll(this.graph.getEdgesFrom(this.graph.getNode(firstmove.destination())));

				secondmoves = validFirstMove(currentplayer, secondedges);

				// Iterate through a set of valid second moves.
				for(Move smv : secondmoves) {
					TicketMove secondmove = (TicketMove) smv;
					Ticket firstticket = firstmove.ticket();
					Ticket secondticket = secondmove.ticket();

					// When the first ticket equals to the secondticket the number of the ticket should be
					// at least two. Otherwise, since the method validFirstMove has checked the number of
					// each first and second ticket is at least one, these first and second moves could
					// generate a valid double move.
					if(currentplayer.hasTickets(secondticket, 2) || !firstticket.equals(secondticket)){
						doublemoves.add(new DoubleMove(currentplayer.colour(), firstmove, secondmove));
					}
				}
			}
		}
		return doublemoves;
	}

	// Make a set of valid moves for the current player
	private Set<Move> validMove(Colour player) {
		ScotlandYardPlayer currentplayer = this.getCurrentPlayerFromColour(player);
		Set<Move> firstmoves = new HashSet<>();
		Set<Move> moves = new HashSet<>();

		// Get all possible edges from a currentplayer's location.
		List<Edge<Integer, Transport>> firstedges = new ArrayList<>();
		firstedges.addAll(this.graph.getEdgesFrom(this.graph.getNode(currentplayer.location())));

		// Add all valid set of single moves.
		firstmoves = this.validFirstMove(currentplayer, firstedges);
	 	moves.addAll(firstmoves);

		// Add all valid double moves when currentplayer is Mr.X.
		if(currentplayer.isMrX()){
			Set<Move> doublemoves = new HashSet<>();
			doublemoves = this.validDoubleMove(currentplayer, firstmoves);
			moves.addAll(doublemoves);
		} else {

			// Add pass moves for detectives when they haven't got any tickets or they
			// have got tickets but can't make a move to anywhere.
			if(!currentplayer.hasTickets(Ticket.BUS) && !currentplayer.hasTickets(Ticket.TAXI) &&
				 !currentplayer.hasTickets(Ticket.UNDERGROUND)) {
				moves.add(new PassMove(player));
			}

			if(firstmoves.isEmpty() && (currentplayer.hasTickets(Ticket.BUS) || currentplayer.hasTickets(Ticket.TAXI) ||
				 currentplayer.hasTickets(Ticket.UNDERGROUND))) {
				moves.add(new PassMove(player));
			}
		}
		return moves;
	}

	// Implement accept method in Consumer<Move> interface.
	@Override
	public void accept(Move move) {

		// Check the validity of a given move.
		requireNonNull(move);
		if(!this.validMove(move.colour()).contains(move)) {
			throw new IllegalArgumentException("The move chosen is not valid");
		}

		// Update playerindex.
		this.playerindex = (this.playerindex + 1) % (this.scotlandyardplayers.size());
		ScotlandYardPlayer currentplayer = this.scotlandyardplayers.get(this.playerindex);

		// Make a move.
		move.visit(this);

		// Notify spectators if game is over.
		if(this.isGameOver()) {
			for(Spectator spectator : this.spectators) {
				spectator.onGameOver(this, this.getWinningPlayers());
			}
		}

		// Notify spectators that a rotation is finished.
		if(currentplayer.isMrX() && !this.isGameOver()) {
			for(Spectator spectator : this.spectators) {
				spectator.onRotationComplete(this);
			}
		}

		// Call makeMove to the next player.
		if(!currentplayer.isMrX() && !this.isGameOver()) {
			Set<Move> moves = this.validMove(currentplayer.colour());
			currentplayer.player().makeMove(this, currentplayer.location(), moves, this);
		}
	}

	// Implementation of visit methods in MoveVisitor interface.
	@Override
	public void visit(PassMove move){
		// Notify spectators each move.
		for(Spectator spectator : this.spectators) {spectator.onMoveMade(this, move);}
	}

	@Override
	public void visit(TicketMove move) {

		// Decrement player index since a player who is on it's move is one index less
		// than playerindex.
		int idx = this.playerindex;
		if(idx >= 1) {idx--;} else if(idx == 0) {idx = this.scotlandyardplayers.size() - 1;}
		ScotlandYardPlayer playeronmove = this.scotlandyardplayers.get(idx);
		ScotlandYardPlayer mrX = this.scotlandyardplayers.get(0);

		if(playeronmove.isMrX()) { // Mr.X's move.
			// Update location.
			playeronmove.location(move.destination());
			this.mrXcurrentloc = playeronmove.location();

			// Update Mr.X's last known location to currentlocation at a reveal round.
			if(this.rounds.get(this.ROUND)) {
				this.mrXlastloc = this.mrXcurrentloc;
			}

			// Discard a used ticket.
			playeronmove.removeTicket(move.ticket());

			// Increment round.
			this.ROUND ++;

			// Notify spectators new round is started.
			for(Spectator spectator : this.spectators) {
				spectator.onRoundStarted(this, this.ROUND);
			}

			// Notify spectators each move.
			for(Spectator spectator : this.spectators) {
				if(!this.rounds.get(this.ROUND - 1)) {
					spectator.onMoveMade(this, new TicketMove(mrX.colour(), move.ticket(), this.mrXlastloc));
				} else {spectator.onMoveMade(this, move);}
			}
		} else { // Detectives' move.

			// Update location of detectives.
			playeronmove.location(move.destination());

			// Discard a used ticket.
			playeronmove.removeTicket(move.ticket());

			// Give used ticket to Mr.X.
			mrX.addTicket(move.ticket());

			// Notify spectators each move
			for(Spectator spectator : this.spectators) {spectator.onMoveMade(this, move);}
		}

	}

	@Override
	public void visit(DoubleMove move) {
		ScotlandYardPlayer mrX = this.scotlandyardplayers.get(0);

		// Discard a double move ticket.
		mrX.removeTicket(Ticket.DOUBLE);

		// Notify spectators double move has made.
		// A location has not to be revealed when it's a hidden round.
		for(Spectator spectator : this.spectators) {
			int d1 = 0; int d2 = 0;
			if(this.rounds.get(this.ROUND) && this.rounds.get(this.ROUND + 1)) {d1 = move.firstMove().destination(); d2 = move.secondMove().destination();}
			if(this.rounds.get(this.ROUND) && !this.rounds.get(this.ROUND + 1)) {d1 = move.firstMove().destination(); d2 = d1;}
			if(!this.rounds.get(this.ROUND) && this.rounds.get(this.ROUND + 1)) {d1 = this.mrXlastloc; d2 = move.secondMove().destination();}
			if(!this.rounds.get(this.ROUND) && !this.rounds.get(this.ROUND + 1)) {d1 = this.mrXlastloc; d2 = d1;}
			spectator.onMoveMade(this, new DoubleMove(mrX.colour(), move.firstMove().ticket(), d1,
					move.secondMove().ticket(), d2));
		}
		move.firstMove().visit(this);
		move.secondMove().visit(this);
	}

	// The method that start rotation of each round.
	@Override
	public void startRotate() {
		// TODO
		if(this.ROUND == 0 && this.isGameOver()) {
			throw new IllegalStateException("The game shouldn't be over in the start round");
		 }
		if(!this.isGameOver()) {
				ScotlandYardPlayer player = this.scotlandyardplayers.get(this.playerindex);
				Set<Move> moves = this.validMove(player.colour());
				player.player().makeMove(this, player.location(), moves, this);
			}
	}

	// The method that registers a given spectator to the collection of spectators.
	@Override
	public void registerSpectator(Spectator spectator) {
		// TODO
		if(this.spectators.contains(spectator)) {
			throw new IllegalArgumentException("Given spectator already exists");
		}
		this.spectators.add(requireNonNull(spectator));
	}

	// The method that unregisters a given spectator to the set of spectators.
	@Override
	public void unregisterSpectator(Spectator spectator) {
		// TODO
		if(!this.spectators.contains(requireNonNull(spectator))) {
			throw new IllegalArgumentException("Given spectator hasn't registered");
		}
		this.spectators.remove(spectator);
	}

	// The method gives out an unmodifiable collection of spectators.
	@Override
	public Collection<Spectator> getSpectators() {
		// TODO
		return Collections.unmodifiableCollection(this.spectators);
	}

	// The method gives out an unmodifiable list of Colours.
	@Override
	public List<Colour> getPlayers() {
		// TODO
		ArrayList<Colour> colours = new ArrayList<> ();
		for(ScotlandYardPlayer player : scotlandyardplayers) {
			colours.add(player.colour());
		}
		return Collections.unmodifiableList(colours);
	}

	// The method gives out an unmodifiable set of winners when a game is over.
	// Otherwise, just return an empty set.
	@Override
	public Set<Colour> getWinningPlayers() {
		// TODO
		if(this.isGameOver()) return Collections.unmodifiableSet(this.winners);
		else return Collections.emptySet();
	}

	// The method that gives out the location of a given player.
	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		// TODO
		Integer loc;

		// If a player is Mr.X. Gives out Mr.X's last known location.
		if (colour.equals(BLACK)) {
			loc = this.mrXlastloc;
			return Optional.of(loc);
		}
		// When a player is one of detectives.
		for(ScotlandYardPlayer player : scotlandyardplayers) {
			if(player.colour().equals(colour)) {
				loc = player.location();
				return Optional.of(loc);
			}
		}
		// When a colour is neither of Mr.X nor one of detectives.
		return Optional.empty();
	}

	// The method that gives out a player's number of given type of ticket.
	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		// TODO
		Integer numofticket;
		for(ScotlandYardPlayer player : scotlandyardplayers) {
			if(player.colour().equals(colour)) {
				numofticket = player.tickets().get(ticket);
				return Optional.of(numofticket);
			}
		}
		return Optional.empty();
	}

	// The method to check if all detectives have no tickets left.
	private boolean allDetectivesStuck() {
		List<ScotlandYardPlayer> players = this.scotlandyardplayers;
		for(ScotlandYardPlayer player : players) {
			if(player.isDetective() && (player.hasTickets(Ticket.BUS) ||
			player.hasTickets(Ticket.TAXI) || player.hasTickets(Ticket.UNDERGROUND))) {
			return false;
			}
		}
		return true;
	}

	// Check if Mr.X is at the same node with any of detectives
	private boolean mrXCaptured() {
		List<ScotlandYardPlayer> players = this.scotlandyardplayers;
		ScotlandYardPlayer mrX = players.get(0);
		for(ScotlandYardPlayer player : players) {
			if(player.isDetective() && player.location() == mrX.location()) return true;
		}
		return false;
	}

	// The method to check if a game is over and add the winners to the set of
	// winning players.
	@Override
	public boolean isGameOver() {
		// TODO
		boolean res = false;
		int endround = this.rounds.size() - 1;
		Set<Move> mrXValidMoves = this.validMove(BLACK);
		ScotlandYardPlayer currentplayer = this.scotlandyardplayers.get(this.playerindex);

		if(this.ROUND > endround && currentplayer.isMrX()) {
			// Mr.X wins.
			if(!this.mrXCaptured()) {this.winners.add(BLACK);}
			res = true;
		}
		if(this.allDetectivesStuck()) {
			// Mr.X wins.
			if(!this.mrXCaptured()) {this.winners.add(BLACK);}
			res = true;
		}
		if(mrXValidMoves.isEmpty() && currentplayer.isMrX()) {
			// Detectives win
			for(ScotlandYardPlayer player : this.scotlandyardplayers) {
				if(player.isDetective()) this.winners.add(player.colour());
			}
			res = true;
		}
		if(this.mrXCaptured()) {
			// Detectives win.
			for(ScotlandYardPlayer player : this.scotlandyardplayers) {
				if(player.isDetective()) this.winners.add(player.colour());
			}
			res = true;
		}
		return res;
	}

	// The method to get current player's Colour.
	@Override
	public Colour getCurrentPlayer() {
		// TODO
		return this.scotlandyardplayers.get(this.playerindex).colour();
	}

	// The method to get current round.
	@Override
	public int getCurrentRound() {
		// TODO
		return this.ROUND;
	}

	// The method to get the list of rounds.
	@Override
	public List<Boolean> getRounds() {
		// TODO
		return Collections.unmodifiableList(this.rounds);
	}

	// The method to get the graph of a view.
	@Override
	public Graph<Integer, Transport> getGraph() {
		// TODO
		return new ImmutableGraph<>(this.graph);
	}

}
