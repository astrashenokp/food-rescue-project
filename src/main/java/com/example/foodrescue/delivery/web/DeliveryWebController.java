package com.example.foodrescue.delivery.web;

import com.example.foodrescue.common.BusinessException;
import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.delivery.ConfirmationRequest;
import com.example.foodrescue.delivery.DeliveryRequest;
import com.example.foodrescue.delivery.DeliveryService;
import com.example.foodrescue.delivery.PickupRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/deliveries")
public class DeliveryWebController {

    private final DeliveryService deliveryService;

    public DeliveryWebController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VOLUNTEER', 'COORDINATOR')")
    public String list(Model model) {
        model.addAttribute("reservedLots", deliveryService.getReservedLots());
        model.addAttribute("deliveries", deliveryService.getDeliveries());
        return "delivery/deliveries";
    }

    @GetMapping("/{lotId}")
    @PreAuthorize("hasAnyRole('VOLUNTEER', 'COORDINATOR')")
    public String details(@PathVariable UUID lotId, Model model) {
        addDetails(model, lotId);
        return "delivery/details";
    }

    @PostMapping("/{lotId}/pickup")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public String pickup(@PathVariable UUID lotId,
                         @Valid @ModelAttribute("pickupForm") PickupForm form,
                         BindingResult errors, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!errors.hasErrors()) {
            try {
                deliveryService.pickup(lotId, new PickupRequest(form.actualWeightKg()));
            } catch (ForbiddenActionException ex) {
                throw ex;
            } catch (BusinessException ex) {
                errors.reject("business", ex.getMessage());
            }
        }
        if (errors.hasErrors()) {
            addDetails(model, lotId);
            return "delivery/details";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Отримання лоту зафіксовано.");
        return "redirect:/deliveries/" + lotId;
    }

    @PostMapping("/{lotId}/deliver")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public String deliver(@PathVariable UUID lotId,
                          @Valid @ModelAttribute("deliverForm") DeliverForm form,
                          BindingResult errors, Model model,
                          RedirectAttributes redirectAttributes) {
        if (!errors.hasErrors()) {
            try {
                deliveryService.deliver(lotId, new DeliveryRequest(form.destinationPointId()));
            } catch (ForbiddenActionException ex) {
                throw ex;
            } catch (BusinessException ex) {
                errors.reject("business", ex.getMessage());
            }
        }
        if (errors.hasErrors()) {
            addDetails(model, lotId);
            return "delivery/details";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Передачу в пункт призначення зафіксовано.");
        return "redirect:/deliveries/" + lotId;
    }

    @PostMapping("/{lotId}/confirm")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public String confirm(@PathVariable UUID lotId,
                          @Valid @ModelAttribute("confirmationForm") ConfirmationForm form,
                          BindingResult errors, Model model,
                          RedirectAttributes redirectAttributes) {
        if (!errors.hasErrors()) {
            try {
                deliveryService.confirm(lotId, new ConfirmationRequest(
                        form.confirmationCode(), form.receivedWeightKg()));
            } catch (ForbiddenActionException ex) {
                throw ex;
            } catch (BusinessException ex) {
                errors.reject("business", ex.getMessage());
            }
        }
        if (errors.hasErrors()) {
            addDetails(model, lotId);
            return "delivery/details";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Результат підтвердження збережено.");
        return "redirect:/deliveries/" + lotId;
    }

    private void addDetails(Model model, UUID lotId) {
        LotResponse lot = deliveryService.getLotOverview(lotId);
        model.addAttribute("lot", lot);
        model.addAttribute("history", deliveryService.getHistory(lotId));
        model.addAttribute("points", deliveryService.getDestinationPoints());
        if (lot.status() == LotStatus.PICKED_UP || lot.status() == LotStatus.DELIVERED
                || lot.status() == LotStatus.CONFIRMED || lot.status() == LotStatus.DISPUTED) {
            model.addAttribute("delivery", deliveryService.getDelivery(lotId));
        }
        if (!model.containsAttribute("pickupForm")) {
            model.addAttribute("pickupForm", new PickupForm(null));
        }
        if (!model.containsAttribute("deliverForm")) {
            model.addAttribute("deliverForm", new DeliverForm(null));
        }
        if (!model.containsAttribute("confirmationForm")) {
            model.addAttribute("confirmationForm", new ConfirmationForm("", null));
        }
    }
}
