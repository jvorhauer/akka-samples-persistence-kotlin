@file:Suppress("UNCHECKED_CAST")

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import akka.persistence.typed.javadsl.RetentionCriteria
import java.time.Duration
import java.time.Instant

fun main(args: Array<String>) {
  println("Main( ${args.joinToString()} )")
}

interface CborSerialized

data class Summary(val items: Map<String, Int>, val checkedOut: Boolean) {
  fun isEmpty() = items.isEmpty()
}

data class State(val items: MutableMap<String, Int> = mutableMapOf(), var checkoutDate: Instant? = null) {
  fun isCheckedOut() = checkoutDate != null
  fun isEmpty() = items.isEmpty()
  fun hasItem(id: String) = items.containsKey(id)
  fun updateItem(id: String, quantity: Int): State {
    if (quantity <= 0) {
      removeItem(id)
    } else {
      items[id] = quantity
    }
    return this
  }

  fun removeItem(id: String): State {
    items.remove(id)
    return this
  }

  fun checkout(now: Instant = Instant.now()): State {
    checkoutDate = now
    return this
  }

  fun toSummary() = Summary(items.toMap(), isCheckedOut())
}


class ShoppingCart(private val cartId: String) :
  EventSourcedBehavior<Command, Event, State>(
    PersistenceId.of("ShoppingCart", cartId),
    SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1)
  ) {
  override fun emptyState(): State = State()

  override fun retentionCriteria(): RetentionCriteria = RetentionCriteria.disabled()  //.snapshotEvery(100, 3)

  override fun commandHandler(): CommandHandler<Command, Event, State> {
    val cmb = newCommandHandlerBuilder()
    cmb.forState { state -> !state.isCheckedOut() }
      .onCommand(AddItem::class.java, this::onAddItem)
      .onCommand(RemoveItem::class.java, this::onRemoveItem)
      .onCommand(AdjustItemQuantity::class.java, this::onAdjustQuantity)
      .onCommand(Checkout::class.java, this::onCheckout)

    cmb.forState { state -> state.isCheckedOut() }
      .onCommand(AddItem::class.java) { cmd -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("cart checked out, can't add item")) } }
      .onCommand(RemoveItem::class.java) { cmd -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("cart checked out, can't remove item")) } }
      .onCommand(AdjustItemQuantity::class.java) { cmd -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("cart checked out, can't adjust")) } }
      .onCommand(Checkout::class.java) { cmd -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("cart already checked out")) } }

    cmb.forAnyState().onCommand(Get::class.java, this::onGet)

    return cmb.build()
  }

  private fun onAddItem(state: State, cmd: AddItem): Effect<Event, State> {
    return when {
      state.hasItem(cmd.id) -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("${cmd.id} already in cart")) }
      cmd.quantity <= 0     -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("quantity must be more than 0")) }
      else -> Effect().persist(ItemAdded(cartId, cmd.id, cmd.quantity))
        .thenRun { newState: State -> cmd.replyTo.tell(StatusReply.success(newState.toSummary())) }
    }
  }

  private fun onRemoveItem(state: State, cmd: RemoveItem): Effect<Event, State> {
    return when {
      state.hasItem(cmd.id) -> Effect().persist(ItemRemoved(cartId, cmd.id))
        .thenRun { newState: State -> cmd.replyTo.tell(StatusReply.success(newState.toSummary())) }
      else -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("can't remove ${cmd.id}: not in cart")) }
    }
  }

  private fun onAdjustQuantity(state: State, cmd: AdjustItemQuantity): Effect<Event, State> {
    return when {
      cmd.quantity <= 0 -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("new quantity must be greater than 0")) }
      state.hasItem(cmd.id) -> Effect().persist(ItemQuantityAdjusted(cartId, cmd.id, cmd.quantity))
        .thenRun { s: State -> cmd.replyTo.tell(StatusReply.success(s.toSummary())) }
      else -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("${cmd.id} not in cart, so not possible to update quantity")) }
    }
  }

  private fun onCheckout(state: State, cmd: Checkout): Effect<Event, State> {
    return when {
      state.isEmpty() -> Effect().none().thenRun { cmd.replyTo.tell(StatusReply.error("empty cart can't checkout")) }
      else -> Effect().persist(CheckedOut(cartId, Instant.now()))
        .thenRun { s: State -> cmd.replyTo.tell(StatusReply.success(s.toSummary())) }
    }
  }

  private fun onGet(state: State, cmd: Get): Effect<Event, State> {
    cmd.replyTo.tell(StatusReply.success(state.toSummary()))
    return Effect().none()
  }


  override fun eventHandler(): EventHandler<State, Event> =
    newEventHandlerBuilder().forAnyState()
      .onEvent(ItemAdded::class.java) { state, event -> state.updateItem(event.id, event.quantity) }
      .onEvent(ItemRemoved::class.java) { state, event -> state.removeItem(event.id) }
      .onEvent(ItemQuantityAdjusted::class.java) { state, event -> state.updateItem(event.id, event.quantity) }
      .onEvent(CheckedOut::class.java) { state, event -> state.checkout(event.time) }
      .build()

  companion object {
    fun create(cartId: String): Behavior<Command> = ShoppingCart(cartId)
  }
}


typealias Reply = ActorRef<StatusReply<Summary>>

interface Command : CborSerialized
data class AddItem(val id: String, val quantity: Int, val replyTo: Reply) : Command
data class RemoveItem(val id: String, val replyTo: Reply) : Command
data class AdjustItemQuantity(val id: String, val quantity: Int, val replyTo: Reply) : Command
data class Get(val replyTo: Reply) : Command
data class Checkout(val replyTo: Reply) : Command

interface Event : CborSerialized
data class ItemAdded(val cartId: String, val id: String, val quantity: Int) : Event
data class ItemRemoved(val cartId: String, val id: String) : Event
data class ItemQuantityAdjusted(val cartId: String, val id: String, val quantity: Int) : Event
data class CheckedOut(val cartId: String, val time: Instant) : Event
