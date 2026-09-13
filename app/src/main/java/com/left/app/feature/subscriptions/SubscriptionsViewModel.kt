package com.left.app.feature.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.SubscriptionRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.domain.AddSubscription
import com.left.app.core.domain.DeleteSubscription
import com.left.app.core.domain.NewSubscription
import com.left.app.core.domain.SubscriptionValidationException
import com.left.app.core.domain.ToggleSubscriptionActive
import com.left.app.core.domain.nextBillingDate
import com.left.app.core.domain.projectedMonthlyAmount
import com.left.app.core.model.BillingCycle
import com.left.app.core.model.Category
import com.left.app.core.model.Subscription
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import com.left.app.core.utils.MoneyParseException
import com.left.app.core.utils.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SubscriptionFormState(val name:String="", val amount:String="", val categoryId:String?=null, val cycle:BillingCycle=BillingCycle.MONTHLY, val nextDate:LocalDate, val reminder:Boolean=true, val error:String?=null)
data class SubscriptionsUiState(val loading:Boolean=true, val currencyCode:String=CurrencyUtils.DEFAULT_CURRENCY_CODE, val subscriptions:List<Subscription> = emptyList(), val categories:List<Category> = emptyList(), val form:SubscriptionFormState, val projectedMonthly:Money=Money.ZERO, val upcoming:List<Subscription> = emptyList(), val saved:Boolean=false, val error:String?=null)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(private val addSubscription:AddSubscription, private val deleteSubscription:DeleteSubscription, private val toggleSubscriptionActive:ToggleSubscriptionActive, private val repository:SubscriptionRepository, categoryRepository:CategoryRepository, userProfileRepository:UserProfileRepository, private val clock:Clock):ViewModel(){
    private val form=MutableStateFlow(SubscriptionFormState(nextDate=LocalDate.now(clock)))
    private val saved=MutableStateFlow(false)
    val uiState:StateFlow<SubscriptionsUiState> = combine(repository.observeAll(), categoryRepository.observeActiveCategories(), userProfileRepository.observeProfile(), form, saved){ subs,cats,profile,formState,savedState ->
        val projected=subs.filter{it.active}.map{ projectedMonthlyAmount(it.amount,it.billingCycle)}.sum()
        val today=LocalDate.now(clock); val until=today.plusDays(7)
        SubscriptionsUiState(false, profile?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE, subs, cats, formState, projected, subs.filter{it.active && it.reminderEnabled && !it.nextBillingDate.isBefore(today) && !it.nextBillingDate.isAfter(until)}.sortedBy{it.nextBillingDate}, savedState, null)
    }.catch{ SafeLogger.e("Subscriptions","subscriptions stream failed",it); emit(SubscriptionsUiState(loading=false, form=form.value, error="Couldn’t load subscriptions. Please reopen the app.")) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionsUiState(form=form.value))
    fun onNameChange(v:String){form.update{it.copy(name=v,error=null)}; saved.value=false}
    fun onAmountChange(v:String){form.update{it.copy(amount=v,error=null)}; saved.value=false}
    fun onCategoryChange(v:String?){form.update{it.copy(categoryId=v,error=null)}; saved.value=false}
    fun onCycleChange(v:BillingCycle){form.update{it.copy(cycle=v,error=null)}; saved.value=false}
    fun onReminderChange(v:Boolean){form.update{it.copy(reminder=v,error=null)}; saved.value=false}
    fun onSave(){ val s=uiState.value; val f=form.value; val amount=try{Money.parse(f.amount,s.currencyCode)}catch(e:MoneyParseException){form.update{it.copy(error="Enter a valid amount")};return}; viewModelScope.launch{try{addSubscription(NewSubscription(f.name,amount,s.currencyCode,f.categoryId,f.cycle,f.nextDate,f.reminder,true)); form.value=SubscriptionFormState(nextDate=LocalDate.now(clock)); saved.value=true}catch(e:SubscriptionValidationException){form.update{it.copy(error=e.message)}}catch(e:Exception){SafeLogger.w("Subscriptions","subscription save failed",e); form.update{it.copy(error="Couldn’t save subscription. Please try again.")}}}}
    fun onToggleActive(id:String,active:Boolean)=viewModelScope.launch{toggleSubscriptionActive(id,active)}
    fun onDelete(id:String)=viewModelScope.launch{deleteSubscription(id)}
    fun onMarkPaid(subscription:Subscription)=viewModelScope.launch{ repository.update(subscription.copy(nextBillingDate=nextBillingDate(subscription.nextBillingDate, subscription.billingCycle))) }
}
