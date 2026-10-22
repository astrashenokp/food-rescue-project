package com.example.foodrescue.delivery.web;

import com.example.foodrescue.common.BusinessException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.delivery.DeliveryService;
import com.example.foodrescue.delivery.DestinationPointRequest;
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

import java.util.Set;

@Controller
@RequestMapping("/destination-points")
@PreAuthorize("hasRole('COORDINATOR')")
public class DestinationPointWebController {

    private final DeliveryService deliveryService;

    public DestinationPointWebController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("points", deliveryService.getDestinationPoints());
        return "delivery/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("form", new CreateDestinationPointForm(null, "", "", "", Set.of()));
        addOptions(model);
        model.addAttribute("editing", false);
        return "delivery/create-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") CreateDestinationPointForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addOptions(model);
            model.addAttribute("editing", false);
            return "delivery/create-form";
        }

        try {
            deliveryService.createDestinationPoint(new DestinationPointRequest(
                    form.organizationId(),
                    form.name(),
                    form.address(),
                    form.workingHours(),
                    form.acceptedCategories()));
        } catch (BusinessException ex) {
            bindingResult.reject("business", ex.getMessage());
            addOptions(model);
            model.addAttribute("editing", false);
            return "delivery/create-form";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Пункт призначення успішно створено.");
        return "redirect:/destination-points";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable java.util.UUID id, Model model) {
        var point = deliveryService.getDestinationPoint(id);
        model.addAttribute("form", new CreateDestinationPointForm(
                point.organizationId(), point.name(), point.address(),
                point.workingHours(), point.acceptedCategories()));
        addOptions(model);
        model.addAttribute("editing", true);
        model.addAttribute("pointId", id);
        return "delivery/create-form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable java.util.UUID id,
            @Valid @ModelAttribute("form") CreateDestinationPointForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                deliveryService.updateDestinationPoint(id, new DestinationPointRequest(
                        form.organizationId(), form.name(), form.address(),
                        form.workingHours(), form.acceptedCategories()));
            } catch (BusinessException ex) {
                bindingResult.reject("business", ex.getMessage());
            }
        }
        if (bindingResult.hasErrors()) {
            addOptions(model);
            model.addAttribute("editing", true);
            model.addAttribute("pointId", id);
            return "delivery/create-form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Пункт призначення оновлено.");
        return "redirect:/destination-points";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable java.util.UUID id, RedirectAttributes redirectAttributes) {
        try {
            deliveryService.deleteDestinationPoint(id);
            redirectAttributes.addFlashAttribute("successMessage", "Пункт призначення видалено.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/destination-points";
    }

    private void addOptions(Model model) {
        model.addAttribute("categories", FoodCategory.values());
    }
}
